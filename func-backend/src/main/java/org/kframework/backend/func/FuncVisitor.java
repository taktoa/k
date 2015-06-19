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
import org.kframework.kore.compile.VisitKORE;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;

import java.util.List;
import java.util.Optional;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.OcamlIncludes.*;

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

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match") || k.klabel().name().equals("#mapChoice") || k.klabel().name().equals("#setChoice");
    }

    @Override
    public String apply(KApply k) {
        if(isLookupKLabel(k)) {
            return apply(BooleanUtils.TRUE);
        } else if(k.klabel().name().equals("#KToken")) {
            //magic down-ness
            Sort sort = Sort(((KToken) ((KSequence) k.klist().items().get(0)).items().get(0)).s());
            String a = apply(sort);
            String b = apply(((KSequence) k.klist().items().get(1)).items().get(0));
            return "KToken (" + a + ", " + b + ")";
        } else if(ppk.functionSet.contains(k.klabel())) {
            return applyFunction(k);
        } else {
            return applyKLabel(k);
        }
    }

    public String applyKLabel(KApply k) {
        return "KApply (" + apply(k.klabel()) + ", " + apply(k.klist().items(), true) + ")";
    }

    public String applyFunction(KApply k) {
        boolean stack = inBooleanExp;
        String hook = ppk.attrLabels.get(Attribute.HOOK_KEY).getOrDefault(k.klabel(), "");
        SyntaxBuilder sb = new SyntaxBuilder();
        // use native &&, ||, not where possible
        if (useNativeBooleanExp && ("#BOOL:_andBool_".equals(hook) || "#BOOL:_andThenBool_".equals(hook))) {
            assert k.klist().items().size() == 2;
            if (!stack) {
                sb.append("[Bool ");
            }
            inBooleanExp = true;
            sb.append("(");
            sb.append(apply(k.klist().items().get(0)));
            sb.append(") && (");
            sb.append(apply(k.klist().items().get(1)));
            sb.append(")");
            if (!stack) {
                sb.append("]");
            }
        } else if (useNativeBooleanExp && ("#BOOL:_orBool_".equals(hook) || "#BOOL:_orElseBool_".equals(hook))) {
            assert k.klist().items().size() == 2;
            if (!stack) {
                sb.append("[Bool ");
            }
            inBooleanExp = true;
            sb.append("(");
            sb.append(apply(k.klist().items().get(0)));
            sb.append(") || (");
            sb.append(apply(k.klist().items().get(1)));
            sb.append(")");
            if (!stack) {
                sb.append("]");
            }
        } else if (useNativeBooleanExp && "#BOOL:notBool_".equals(hook)) {
            assert k.klist().items().size() == 1;
            if (!stack) {
                sb.append("[Bool ");
            }
            inBooleanExp = true;
            sb.append("(not ");
            sb.append(apply(k.klist().items().get(0)));
            sb.append(")");
            if (!stack) {
                sb.append("]");
            }
        } else if (ppk.collectionFor.containsKey(k.klabel()) && !rhs) {
            sb.append(applyKLabel(k));
            sb.append(" :: []");
        } else {
            if(ppk.attrLabels.get(Attribute.PREDICATE_KEY).keySet().contains(k.klabel())) {
                Sort s = Sort(ppk.attrLabels.get(Attribute.PREDICATE_KEY).get(k.klabel()));
                String hook2 = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(s, "");
                if(sortHooks.containsKey(hook2) && k.klist().items().size() == 1) {
                    KSequence item = (KSequence) k.klist().items().get(0);
                    if (item.items().size() == 1 &&
                        vars.containsKey(item.items().get(0))) {
                        Optional<String> varSort = item.items().get(0).att().<String>getOptional(Attribute.SORT_KEY);
                        if (varSort.isPresent() && varSort.get().equals(s.name())) {
                            // this has been subsumed by a structural check on the builtin data type
                            sb.append(apply(BooleanUtils.TRUE));
                            return sb.toString();
                        }
                    }
                }
                if (s.equals(Sorts.KItem())
                    && k.klist().items().size() == 1
                    && k.klist().items().get(0) instanceof KSequence) {
                    KSequence item = (KSequence) k.klist().items().get(0);
                    if (item.items().size() == 1) {
                        sb.append(apply(BooleanUtils.TRUE));
                        return sb.toString();
                    }
                }
            }
            if(stack) {
                sb.append("(isTrue ");
            }
            inBooleanExp = false;
            sb.append("(");
            sb.append(encodeStringToFunction(k.klabel().name()));
            sb.append("(");
            sb.append(apply(k.klist().items(), true));
            sb.append(") Guard.empty)");
            if(stack) {
                sb.append(")");
            }
        }
        inBooleanExp = stack;
        return sb.toString();
    }

    @Override
    public String apply(KRewrite k) {
        throw new AssertionError("unexpected rewrite");
    }

    @Override
    public String apply(KToken k) {
        if (useNativeBooleanExp && inBooleanExp && k.sort().equals(Sorts.Bool())) {
            return k.s();
        }
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(k.sort(), "");
        if (sortHooks.containsKey(hook)) {
            return sortHooks.get(hook).apply(k.s());
        }
        return "KToken (" + apply(k.sort()) + ", " + StringUtil.enquoteCString(k.s()) + ")";
    }

    @Override
    public String apply(KVariable k) {
        if(rhs) {
            return applyVarRhs(k, vars);
        } else {
            return applyVarLhs(k, vars);
        }
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

    @Override
    public String apply(KSequence k) {
        if (useNativeBooleanExp && k.items().size() == 1 && inBooleanExp) {
            return apply(k.items().get(0));
        }
        checkKSequence(k);
        return String.format("(%s)", apply(k.items(), false));
    }

    public String getSortOfVar(K k) {
        return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
    }

    @Override
    public String apply(InjectedKLabel k) {
        return String.format("InjectedKLabel (%s)", apply(k.klabel()));
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

    private boolean isList(K item, boolean klist) {
        return !klist && ((item instanceof KVariable && getSortOfVar(item).equals("K"))
                          || item instanceof KSequence
                          || (item instanceof KApply && ppk.functionSet.contains(((KApply) item).klabel())));
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
        if (sortHooks.containsKey(hook)) {
            return "(" + s.name() + " _" + " as " + varName + ")";
        }
        return varName;
    }
}
