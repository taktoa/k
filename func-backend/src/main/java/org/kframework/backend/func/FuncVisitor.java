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

public class FuncVisitor extends VisitKORE {
    private final PreprocessedKORE ppk;
    private final SyntaxBuilder sb;
    private final boolean rhs;
    private final SetMultimap<KVariable, String> vars;
    private final boolean useNativeBooleanExp;
    private boolean inBooleanExp;

    public FuncVisitor(PreprocessedKORE ppk,
                       SyntaxBuilder sb,
                       boolean rhs,
                       SetMultimap<KVariable, String> vars,
                       boolean useNativeBooleanExp) {
        this.ppk = ppk;
        this.sb = sb;
        this.rhs = rhs;
        this.vars = vars;
        this.useNativeBooleanExp = useNativeBooleanExp;
        this.inBooleanExp = useNativeBooleanExp;
    }

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match") || k.klabel().name().equals("#mapChoice") || k.klabel().name().equals("#setChoice");
    }
    
    @Override
    public Void apply(KApply k) {
        if (isLookupKLabel(k)) {
            apply(BooleanUtils.TRUE);
        } else if (k.klabel().name().equals("#KToken")) {
            //magic down-ness
            sb.append("KToken (");
            Sort sort = Sort(((KToken) ((KSequence) k.klist().items().get(0)).items().get(0)).s());
            apply(sort);
            sb.append(", ");
            apply(((KSequence) k.klist().items().get(1)).items().get(0));
            sb.append(")");
        } else if (ppk.functionSet.contains(k.klabel())) {
            applyFunction(k);
        } else {
            applyKLabel(k);
        }
        return null;
    }

    public void applyKLabel(KApply k) {
        sb.append("KApply (");
        apply(k.klabel());
        sb.append(", ");
        apply(k.klist().items(), true);
        sb.append(")");
    }

    public void applyFunction(KApply k) {
        boolean stack = inBooleanExp;
        String hook = ppk.attrLabels.get(Attribute.HOOK_KEY).getOrDefault(k.klabel(), "");
        // use native &&, ||, not where possible
        if (useNativeBooleanExp && ("#BOOL:_andBool_".equals(hook) || "#BOOL:_andThenBool_".equals(hook))) {
            assert k.klist().items().size() == 2;
            if (!stack) {
                sb.append("[Bool ");
            }
            inBooleanExp = true;
            sb.append("(");
            apply(k.klist().items().get(0));
            sb.append(") && (");
            apply(k.klist().items().get(1));
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
            apply(k.klist().items().get(0));
            sb.append(") || (");
            apply(k.klist().items().get(1));
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
            apply(k.klist().items().get(0));
            sb.append(")");
            if (!stack) {
                sb.append("]");
            }
        } else if (ppk.collectionFor.containsKey(k.klabel()) && !rhs) {
            applyKLabel(k);
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
                            apply(BooleanUtils.TRUE);
                            return;
                        }
                    }
                }
                if (s.equals(Sorts.KItem())
                    && k.klist().items().size() == 1
                    && k.klist().items().get(0) instanceof KSequence) {
                    KSequence item = (KSequence) k.klist().items().get(0);
                    if (item.items().size() == 1) {
                        apply(BooleanUtils.TRUE);
                        return;
                    }
                }
            }
            if (stack) {
                sb.append("(isTrue ");
            }
            inBooleanExp = false;
            sb.append("(");
            sb.append(encodeStringToFunction(k.klabel().name()));
            sb.append("(");
            apply(k.klist().items(), true);
            sb.append(") Guard.empty)");
            if (stack) {
                sb.append(")");
            }
        }
        inBooleanExp = stack;
    }

    @Override
    public Void apply(KRewrite k) {
        throw new AssertionError("unexpected rewrite");
    }

    @Override
    public Void apply(KToken k) {
        if (useNativeBooleanExp && inBooleanExp && k.sort().equals(Sorts.Bool())) {
            sb.append(k.s());
            return null;
        }
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(k.sort(), "");
        if (sortHooks.containsKey(hook)) {
            sb.append(sortHooks.get(hook).apply(k.s()));
            return null;
        }
        sb.append("KToken (");
        apply(k.sort());
        sb.append(", ");
        sb.append(StringUtil.enquoteCString(k.s()));
        sb.append(")");
        return null;
    }

    @Override
    public Void apply(KVariable k) {
        if (rhs) {
            applyVarRhs(k, sb, vars);
        } else {
            applyVarLhs(k, sb, vars);
        }
        return null;
    }

    @Override
    public Void apply(KSequence k) {
        if (useNativeBooleanExp && k.items().size() == 1 && inBooleanExp) {
            apply(k.items().get(0));
            return null;
        }
        sb.append("(");
        if (!rhs) {
            for (int i = 0; i < k.items().size() - 1; i++) {
                if (isList(k.items().get(i), false)) {
                    throw KEMException.criticalError("Cannot compile KSequence with K variable not at tail.", k.items().get(i));
                }
            }
        }
        apply(k.items(), false);
        sb.append(")");
        return null;
    }

    public String getSortOfVar(K k) {
        return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
    }

    @Override
    public Void apply(InjectedKLabel k) {
        sb.append("InjectedKLabel (");
        apply(k.klabel());
        sb.append(")");
        return null;
    }

    public void apply(List<K> items, boolean klist) {
        for(int i = 0; i < items.size(); i++) {
            K item = items.get(i);
            apply(item);
            if (i == items.size() - 1) {
                if (!isList(item, klist)) {
                    sb.append(" :: []");
                }
            } else {
                if (isList(item, klist)) {
                    sb.append(" @ ");
                } else {
                    sb.append(" :: ");
                }
            }
        }
        if(items.isEmpty()) {
            sb.append("[]");
        }
    }

    private boolean isList(K item, boolean klist) {
        return !klist && ((item instanceof KVariable && getSortOfVar(item).equals("K")) || item instanceof KSequence
                          || (item instanceof KApply && ppk.functionSet.contains(((KApply) item).klabel())));
    }

    public void apply(Sort sort) {
        sb.append(encodeStringToIdentifier(sort));
    }

    public void apply(KLabel klabel) {
        if (klabel instanceof KVariable) {
            apply((KVariable) klabel);
        } else {
            sb.append(encodeStringToIdentifier(klabel));
        }
    }

    private void applyVarRhs(KVariable v, SyntaxBuilder sb, SetMultimap<KVariable, String> vars) {
        sb.append(vars.get(v).iterator().next());
    }

    private void applyVarLhs(KVariable k, SyntaxBuilder sb, SetMultimap<KVariable, String> vars) {
        String varName = encodeStringToVariable(k.name());
        vars.put(k, varName);
        Sort s = Sort(k.att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
        String hook = ppk.attrSorts.get(Attribute.HOOK_KEY).getOrDefault(s, "");
        if (sortHooks.containsKey(hook)) {
            sb.append("(");
            sb.append(s.name());
            sb.append(" _");
            sb.append(" as ");
            sb.append(varName);
            sb.append(")");
            return;
        }
        sb.append(varName);
    }
}
