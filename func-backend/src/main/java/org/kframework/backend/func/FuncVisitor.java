// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.SetMultimap;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.kil.Attribute;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.AbstractKORETransformer;
import org.kframework.kore.Sort;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;

import java.util.List;
import java.util.Optional;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.OCamlIncludes.*;
import static org.kframework.backend.func.FuncUtil.*;

/**
 * Main visitor for converting KORE for the functional backend
 *
 * @author Remy Goldschmidt
 */
public class FuncVisitor extends AbstractKORETransformer<SyntaxBuilder> {
    private final PreprocessedKORE ppk;
    private final boolean rhs;
    private final SetMultimap<KVariable, String> vars;
    private final boolean useNativeBooleanExp;
    private boolean inBooleanExp;

    public FuncVisitor(PreprocessedKORE ppk,
                       boolean rhs,
                       SetMultimap<KVariable, String> vars,
                       boolean useNativeBooleanExp) {
        this.ppk = ppk;
        this.rhs = rhs;
        this.vars = vars;
        this.useNativeBooleanExp = useNativeBooleanExp;
        this.inBooleanExp = useNativeBooleanExp;
    }

    @Override
    public SyntaxBuilder apply(KApply k) {
        KLabel kl = k.klabel();
        if(isVariableKApply(k) && rhs) {
            return newsbf("eval (%s)", applyKLabel(k));
        } else if(isLookupKLabel(kl)) {
            return apply(BooleanUtils.TRUE);
        } else if(isKTokenKLabel(kl)) {
            //magic down-ness

// BUG SPOT? was originally:
// - return String.format("KToken (%s, %s)",
// -  apply(Sort(((KToken) ((KSequence) k.klist().items().get(0)).items().get(0)).s())),
// -  apply(((KSequence) k.klist().items().get(1)).items().get(0)));

            KSequence kseq1 = (KSequence) k.klist().items().get(0);
            KSequence kseq2 = (KSequence) k.klist().items().get(1);
            KToken    ktok1 = (KToken)    kseq1.items().get(0);
            return
                newsb()
                .addValue("KToken")
                .beginParenthesis()
                .append(apply(Sort(ktok1.s())))
                .addKeyword(",")
                .addSpace()
                .append(apply(kseq2.items().get(0)))
                .endParenthesis();
        } else if(isFunctionKLabel(kl) || (isAnywhereKLabel(kl) && rhs)) {
            return applyFunction(k);
        } else {
            return applyKLabel(k);
        }
    }

    @Override
    public SyntaxBuilder apply(KRewrite k) {
        throw new AssertionError("unexpected rewrite");
    }

    @Override
    public SyntaxBuilder apply(KToken k) {
        if(   useNativeBooleanExp
           && inBooleanExp
           && k.sort().equals(Sorts.Bool())) {
            return newsb(k.s());
        } else {
            String hook = ppk.attrSorts
                             .get(Attribute.HOOK_KEY)
                             .getOrDefault(k.sort(), "");
            if(sortHooks.containsKey(hook)) {
                return sortHooks.get(hook).apply(k.s());
            } else {
                return newsbf("KToken (%s, %s)",
                              apply(k.sort()),
                              StringUtil.enquoteCString(k.s()));
            }
        }
    }

    @Override
    public SyntaxBuilder apply(KVariable k) {
        if(rhs) {
            return applyVarRhs(k, vars);
        } else {
            return applyVarLhs(k, vars);
        }
    }

    @Override
    public SyntaxBuilder apply(KSequence k) {
        if(   useNativeBooleanExp
           && k.items().size() == 1
           && inBooleanExp) {
            return apply(k.items().get(0));
        } else {
            checkKSequence(k);
            return newsbf("(%s)", apply(k.items(), false));
        }
    }

    @Override
    public SyntaxBuilder apply(InjectedKLabel k) {
        return newsbf("InjectedKLabel (%s)", apply(k.klabel()));
    }

    public SyntaxBuilder applyKLabel(KApply k) {
        return newsbf("KApply (%s, %s)",
                     apply(k.klabel()),
                     apply(k.klist().items(), true));
    }

    public SyntaxBuilder applyBoolMonad(KApply k, String fmt) {
        assert k.klist().items().size() == 1;
        inBooleanExp = true;
        return newsbf(fmt,
                      apply(k.klist().items().get(0)));
    }

    public SyntaxBuilder applyBoolDyad(KApply k, String fmt) {
        assert k.klist().items().size() == 2;
        inBooleanExp = true;
        return newsbf(fmt,
                      apply(k.klist().items().get(0)),
                      apply(k.klist().items().get(1)));
    }

    public Optional<SyntaxBuilder> applyPredicateFunction(KApply k) {
        Sort s = Sort(ppk.attrLabels.get(Attribute.PREDICATE_KEY).get(k.klabel()));
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(s, "");
        if(sortHooks.containsKey(hook) && k.klist().items().size() == 1) {
            KSequence item = (KSequence) k.klist().items().get(0);
            if(item.items().size() == 1 && vars.containsKey(item.items().get(0))) {
                Optional<String> varSort = item.items()
                    .get(0)
                    .att()
                    .<String>getOptional(Attribute.SORT_KEY);
                if(varSort.isPresent() && varSort.get().equals(s.name())) {
                    return Optional.of(apply(BooleanUtils.TRUE));
                }
            }
        }
        if(s.equals(Sorts.KItem())
           && k.klist().items().size() == 1
           && k.klist().items().get(0) instanceof KSequence) {
            KSequence item = (KSequence) k.klist().items().get(0);
            if(item.items().size() == 1) {
                return Optional.of(apply(BooleanUtils.TRUE));
            }
        }
        return Optional.empty();
    }

    public SyntaxBuilder applyFunction(KApply k) {
        boolean stack = inBooleanExp;
        SyntaxBuilder res;
        String hook = ppk.attrLabels
                         .get(Attribute.HOOK_KEY)
                         .getOrDefault(k.klabel(), "");

        if(isNativeAnd(hook)) {
            res = applyBoolDyad(k, stack ? "(%s) && (%s)" : "[Bool (%s) && (%s)]");
        } else if(isNativeOr(hook)) {
            res = applyBoolDyad(k, stack ? "(%s) || (%s)" : "[Bool (%s) || (%s)]");
        } else if(isNativeNot(hook)) {
            res = applyBoolMonad(k, stack ? "(not (%s))"  : "[Bool (not (%s))]");
        } else if(ppk.collectionFor.containsKey(k.klabel()) && !rhs) {
            res = newsb()
                .append(applyKLabel(k))
                .addSpace()
                .addKeyword("::")
                .addSpace()
                .addValue("[]");
        } else {
            if(ppk.attrLabels
                  .get(Attribute.PREDICATE_KEY)
                  .keySet()
                  .contains(k.klabel())) {
                Optional<SyntaxBuilder> predRes = applyPredicateFunction(k);
                if(predRes.isPresent()) {
                    return predRes.get();
                }
            }

            inBooleanExp = false;

            String fmt = stack ? "(isTrue (%s(%s) Guard.empty))"
                               : "(%s(%s) Guard.empty)";
            res = newsb().appendf(fmt,
                                  encodeStringToFunction(k.klabel().name()),
                                  apply(k.klist().items(), true));
        }

        inBooleanExp = stack;
        return res;
    }

    public SyntaxBuilder apply(List<K> items, boolean klist) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for(int i = 0; i < items.size(); i++) {
            K item = items.get(i);
            sb.append(apply(item));
            if(i == items.size() - 1) {
                if(!isList(item, klist)) {
                    sb.addSpace();
                    sb.addKeyword("::");
                    sb.addSpace();
                    sb.addValue("[]");
                }
            } else {
                if(isList(item, klist)) {
                    sb.addSpace();
                    sb.addKeyword("@");
                    sb.addSpace();
                } else {
                    sb.addSpace();
                    sb.addKeyword("::");
                    sb.addSpace();
                }
            }
        }
        if(items.isEmpty()) {
            sb.addValue("[]");
        }

        return sb;
    }

    public SyntaxBuilder apply(Sort sort) {
        return newsb(encodeStringToIdentifier(sort));
    }

    public SyntaxBuilder apply(KLabel klabel) {
        if(klabel instanceof KVariable) {
            return apply((KVariable) klabel);
        } else {
            return newsb(encodeStringToIdentifier(klabel));
        }
    }

    private SyntaxBuilder applyVarRhs(KVariable v,
                                      SetMultimap<KVariable, String> vars) {
        return newsb(vars.get(v).iterator().next());
    }

    private SyntaxBuilder applyVarLhs(KVariable k,
                                      SetMultimap<KVariable, String> vars) {
        String varName = encodeStringToVariable(k.name());

        vars.put(k, varName);

        Sort s = Sort(k.att()
                       .<String>getOptional(Attribute.SORT_KEY)
                       .orElse(""));

        String hook = ppk.attrSorts
                         .get(Attribute.HOOK_KEY)
                         .getOrDefault(s, "");

        if(sortHooks.containsKey(hook)) {
            return newsb()
                .beginParenthesis()
                .addValue(s.name())
                .addSpace()
                .addValue("_")
                .addSpace()
                .addKeyword("as")
                .addSpace()
                .addValue(varName)
                .endParenthesis();
        }

        return newsb(varName);
    }

    private void checkKSequence(KSequence k) {
        String kseqError = "Cannot compile KSequence with K variable not at tail.";
        if(!rhs) {
            for(int i = 0; i < k.items().size() - 1; i++) {
                if(isList(k.items().get(i), false)) {
                    throw kemCriticalErrorF(k.items().get(i), kseqError);
                }
            }
        }
    }

    public String getSortOfVar(K k) {
        return k.att()
                .<String>getOptional(Attribute.SORT_KEY)
                .orElse("K");
    }


    private boolean isNativeAnd(String hook) {
        boolean res = useNativeBooleanExp;
        res        &=    "#BOOL:_andThenBool_".equals(hook)
                      || "#BOOL:_andBool_".equals(hook);
        return res;
    }

    private boolean isNativeOr(String hook) {
        boolean res = useNativeBooleanExp;
        res        &=    "#BOOL:_orBool_".equals(hook)
                      || "#BOOL:_orElseBool_".equals(hook);
        return res;
    }

    private boolean isNativeNot(String hook) {
        return useNativeBooleanExp && "#BOOL:notBool_".equals(hook);
    }

    private boolean isList(K item, boolean klist) {
        boolean isKSequence = item instanceof KSequence;
        boolean isKVariable = item instanceof KVariable;
        boolean isKApply    = item instanceof KApply;
        boolean sortIsK     = getSortOfVar(item).equals("K");

        if(klist)                  { return false; }
        if(isKSequence)            { return true; }
        if(isKVariable && sortIsK) { return true; }

        if(isKApply) {
            KLabel kl = ((KApply) item).klabel();
            if(isFunctionKLabel(kl))    { return true; }
            if(! rhs)                   { return false; }
            if(kl instanceof KVariable) { return true; }
            if(isAnywhereKLabel(kl))    { return true; }
        }

        return false;
    }

    private boolean isVariableKApply(KApply k) {
        return k.klabel() instanceof KVariable;
    }

    private boolean isLookupKLabel(KLabel k) {
        return k.name().equals("#match")
            || k.name().equals("#mapChoice")
            || k.name().equals("#setChoice");
    }

    private boolean isKTokenKLabel(KLabel k) {
        return k.name().equals("#KToken");
    }

    private boolean isFunctionKLabel(KLabel k) {
        return ppk.functionSet.contains(k);
    }

    private boolean isAnywhereKLabel(KLabel k) {
        return ppk.anywhereSet.contains(k);
    }
}
