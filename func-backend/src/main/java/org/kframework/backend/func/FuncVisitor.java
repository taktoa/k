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


/**
 * Main visitor for converting KORE for the functional backend
 *
 * @author Remy Goldschmidt
 */
public class FuncVisitor extends AbstractKORETransformer<String> {
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
    public String apply(KApply k) {
        KLabel kl = k.klabel();
        if(k.klabel() instanceof KVariable && rhs) {
            return String.format("eval (%s)", applyKLabel(k));
        }

        if(isLookupKLabel(kl)) {
            return apply(BooleanUtils.TRUE);
        }

        if(isKTokenKLabel(kl)) {
            //magic down-ness
            return String.format("KToken (%s, %s)",
                                 apply(Sort(((KToken) ((KSequence) k.klist().items().get(0)).items().get(0)).s())),
                                 apply(((KSequence) k.klist().items().get(1)).items().get(0)));
        }

        if(isFunctionKLabel(kl) || isAnywhereKLabel(kl)) {
            return applyFunction(k);
        }

        return applyKLabel(k);
    }

    @Override
    public String apply(KRewrite k) {
        throw new AssertionError("unexpected rewrite");
    }

    @Override
    public String apply(KToken k) {
        if(useNativeBooleanExp && inBooleanExp && k.sort().equals(Sorts.Bool())) {
            return k.s();
        }
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(k.sort(), "");
        if(sortHooks.containsKey(hook)) {
            return sortHooks.get(hook).apply(k.s());
        }
        return String.format("KToken (%s, %s)", apply(k.sort()), StringUtil.enquoteCString(k.s()));
    }

    @Override
    public String apply(KVariable k) {
        if(rhs) {
            return applyVarRhs(k, vars);
        } else {
            return applyVarLhs(k, vars);
        }
    }

    @Override
    public String apply(KSequence k) {
        if(useNativeBooleanExp && k.items().size() == 1 && inBooleanExp) {
            return apply(k.items().get(0));
        }
        checkKSequence(k);
        return String.format("(%s)", apply(k.items(), false));
    }

    @Override
    public String apply(InjectedKLabel k) {
        return String.format("InjectedKLabel (%s)", apply(k.klabel()));
    }

    public String applyKLabel(KApply k) {
        return String.format("KApply (%s, %s)",
                             apply(k.klabel()),
                             apply(k.klist().items(), true));
    }

    public String applyBoolMonad(KApply k, String fmt) {
        assert k.klist().items().size() == 1;
        inBooleanExp = true;
        return String.format(fmt, apply(k.klist().items().get(0)));
    }

    public String applyBoolDyad(KApply k, String fmt) {
        assert k.klist().items().size() == 2;
        inBooleanExp = true;
        return String.format(fmt,
                             apply(k.klist().items().get(0)),
                             apply(k.klist().items().get(1)));
    }

    public Optional<String> applyPredicateFunction(KApply k) {
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

    public String applyFunction(KApply k) {
        boolean stack = inBooleanExp;
        String res = "";
        String hook = ppk.attrLabels.get(Attribute.HOOK_KEY).getOrDefault(k.klabel(), "");
        if(useNativeBooleanExp && ("#BOOL:_andBool_".equals(hook) || "#BOOL:_andThenBool_".equals(hook))) {
            res = applyBoolDyad(k, stack ? "(%s) && (%s)" : "[Bool (%s) && (%s)]");
        } else if(useNativeBooleanExp && ("#BOOL:_orBool_".equals(hook) || "#BOOL:_orElseBool_".equals(hook))) {
            res = applyBoolDyad(k, stack ? "(%s) || (%s)" : "[Bool (%s) || (%s)]");
        } else if(useNativeBooleanExp && "#BOOL:notBool_".equals(hook)) {
            res = applyBoolMonad(k, stack ? "(not (%s))" : "[Bool (not (%s))]");
        } else if(ppk.collectionFor.containsKey(k.klabel()) && !rhs) {
            res = String.format("%s :: []", applyKLabel(k));
        } else {
            if(ppk.attrLabels.get(Attribute.PREDICATE_KEY).keySet().contains(k.klabel())) {
                Optional<String> predRes = applyPredicateFunction(k);
                if(predRes.isPresent()) {
                    return predRes.get();
                }
            }
            String fmt = stack ? "(isTrue (%s(%s) Guard.empty))" : "(%s(%s) Guard.empty)";
            inBooleanExp = false;
            res = String.format(fmt,
                                encodeStringToFunction(k.klabel().name()),
                                apply(k.klist().items(), true));
        }
        inBooleanExp = stack;
        return res;
    }

    public String apply(List<K> items, boolean klist) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for(int i = 0; i < items.size(); i++) {
            K item = items.get(i);
            sb.append(apply(item));
            if(i == items.size() - 1) {
                if(!isList(item, klist)) {
                    sb.append(" :: []");
                }
            } else {
                if(isList(item, klist)) {
                    sb.append(" @ ");
                } else {
                    sb.append(" :: ");
                }
            }
        }
        if(items.isEmpty()) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public String apply(Sort sort) {
        return encodeStringToIdentifier(sort);
    }

    public String apply(KLabel klabel) {
        if(klabel instanceof KVariable) {
            return apply((KVariable) klabel);
        } else {
            return encodeStringToIdentifier(klabel);
        }
    }

    private String applyVarRhs(KVariable v, SetMultimap<KVariable, String> vars) {
        return vars.get(v).iterator().next();
    }

    private String applyVarLhs(KVariable k, SetMultimap<KVariable, String> vars) {
        String varName = encodeStringToVariable(k.name());
        vars.put(k, varName);
        Sort s = Sort(k.att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(s, "");
        if(sortHooks.containsKey(hook)) {
            return String.format("(%s _ as %s)", s.name(), varName);
        }
        return varName;
    }

    private void checkKSequence(KSequence k) {
        String kseqError = "Cannot compile KSequence with K variable not at tail.";
        if(!rhs) {
            for(int i = 0; i < k.items().size() - 1; i++) {
                if(isList(k.items().get(i), false)) {
                    throw KEMException.criticalError(kseqError, k.items().get(i));
                }
            }
        }
    }

    public String getSortOfVar(K k) {
        return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
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
