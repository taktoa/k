package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.OcamlIncludes.*;

/*
 * @author: Remy Goldschmidt
 */
public class DefinitionToFunc {
    public static final boolean annotateOutput = true;

    private final KExceptionManager kem;
    private final FileUtil files;
    private final GlobalOptions globalOptions;
    private final KompileOptions kompileOptions;

    private PreprocessedKORE preproc;

    public DefinitionToFunc(KExceptionManager kem,
                            FileUtil files,
                            GlobalOptions globalOptions,
                            KompileOptions kompileOptions) {
        this.kem = kem;
        this.files = files;
        this.globalOptions = globalOptions;
        this.kompileOptions = kompileOptions;
    }

    private FuncAST runtimeCodeToFunc(K k, int depth) {
        SyntaxBuilder sb = new SyntaxBuilder();
        sb.addImport("Def");
        sb.addImport("K");
        sb.addImport("Big_int");
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("_");
        sb.beginLetEquationValue();
        sb.append("print_string(print_k(try(run(");
        Visitor convVisitor = oldConvert(sb, true, HashMultimap.create(), false);
        convVisitor.apply(preproc.runtimeProcess(k));
        sb.append(") (");
        sb.append(Integer.toString(depth));
        sb.append(")) with Stuck c' -> c'))");
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetExpression();
        return new FuncAST(sb.render());
    }

    private FuncAST langDefToFunc(CompiledDefinition def) {
        return new FuncAST(mainConvert());
    }

    public String convert(CompiledDefinition def) {
        preproc = new PreprocessedKORE(def, kem, files, globalOptions, kompileOptions);
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        String p = preproc.prettyPrint(); // DEBUG
        System.out.println(p); // DEBUG
        System.out.println(Integer.toString(p.hashCode())); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        if(1 == 1) { throw KEMException.criticalError("blah"); } // DEBUG
        return langDefToFunc(def).render();
    }

    public String convert(K k, int depth) {
        if(1 == 1) { throw KEMException.criticalError("blah"); } // DEBUG
        return runtimeCodeToFunc(k, depth).render();
    }

    private void addSortType(SyntaxBuilder sb) {
        sb.beginTypeDefinition("sort");
        for (Sort s : iterable(preproc.definedSorts)) {
            sb.beginConstructor();
            sb.append(encodeStringToIdentifier(s));
            sb.endConstructor();
        }
        if (!preproc.definedSorts.contains(Sorts.String())) {
            sb.addConstructor("SortString");
        }
        sb.endTypeDefinition();
    }

    private void addSortOrderFunc(SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("order_sort(s: sort)");
        sb.beginLetEquationValue();
        sb.beginMatchExpression("s");

        int i = 0;

        for (Sort s : iterable(preproc.definedSorts)) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(Integer.toString(i++));
        }

        sb.endMatchExpression();
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetEquation();
    }

    private void addKLabelType(SyntaxBuilder sb) {
        sb.beginTypeDefinition("klabel");
        for (KLabel label : iterable(preproc.definedKLabels)) {
            sb.beginConstructor();
            sb.append(encodeStringToIdentifier(label));
            sb.endConstructor();
        }
        sb.endTypeDefinition();
    }

    private void addKLabelOrderFunc(SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("order_klabel(l: klabel)");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("l");

        int i = 0;

        for (KLabel label : iterable(preproc.definedKLabels)) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(label));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(Integer.toString(i++));
            sb.endMatchEquation();
        }
        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintSortString(SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_sort_string(c: sort) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (Sort s : iterable(preproc.definedSorts)) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(StringUtil.enquoteKString(s.name())));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintSort(SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_sort(c: sort) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (Sort s : iterable(preproc.definedSorts)) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(s.name()));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintKLabel(SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_klabel(c: klabel) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (KLabel label : iterable(preproc.definedKLabels)) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(label));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(ToKast.apply(label)));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }


    private int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"), a2.att().contains("owise"));
    }

    private void addRules(SyntaxBuilder sb) {
        int i = 0;
        for (List<KLabel> component : preproc.functionOrder) {
            boolean inLetrec = false;
            for (KLabel functionLabel : component) {
                if(inLetrec) {
                    sb.addLetrecEquationSeparator();
                } else {
                    sb.beginLetrecExpression();
                    sb.beginLetrecDefinitions();
                }
                sb.beginLetrecEquation();
                String functionName = encodeStringToFunction(functionLabel.name());
                sb.addLetrecEquationName(functionName + " (c: k list) (guards: Guard.t) : k");
                sb.beginLetrecEquationValue();
                sb.beginLetExpression();
                sb.beginLetDefinitions();
                sb.addLetEquation("lbl", encodeStringToIdentifier(functionLabel));
                sb.endLetDefinitions();
                sb.beginLetScope();
                sb.beginMatchExpression("c");
                String hook = preproc.attributesFor.apply(functionLabel).<String>getOptional(Attribute.HOOK_KEY).orElse("");
                if (hooks.containsKey(hook)) {
                    sb.beginMatchEquation();
                    sb.append(hooks.get(hook));
                    sb.endMatchEquation();
                }
                if (predicateRules.containsKey(functionLabel.name())) {
                    sb.beginMatchEquation();
                    sb.append(predicateRules.get(functionLabel.name()));
                    sb.endMatchEquation();
                }

                i = 0;
                for (Rule r : preproc.functionRules.get(functionLabel).stream().sorted(this::sortFunctionRules).collect(Collectors.toList())) {
                    oldConvert(r, sb, true, i++, functionName);
                }
                sb.addMatchEquation("_", "raise (Stuck [KApply(lbl, c)])");
                sb.endMatchExpression();
                sb.endLetScope();
                sb.endLetExpression();
                sb.endLetrecEquationValue();
                sb.endLetrecEquation();
                inLetrec = true;
            }
            sb.endLetrecDefinitions();
            sb.endLetrecExpression();
        }

        boolean hasLookups = false;
        Map<Boolean, List<Rule>> sortedRules = stream(preproc.rules).collect(Collectors.groupingBy(this::hasLookups));

        sb.beginLetrecExpression();
        sb.beginLetrecDefinitions();
        sb.beginLetrecEquation();
        sb.addLetrecEquationName("lookups_step (c: k) (guards: Guard.t) : k");
        sb.beginLetrecEquationValue();
        sb.beginMatchExpression("c");
        i = 0;
        for (Rule r : sortedRules.get(true)) {
            if (!preproc.functionRules.values().contains(r)) {
                oldConvert(r, sb, false, i++, "lookups_step");
            }
        }
        sb.addMatchEquation("_", "raise (Stuck c)");
        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
        sb.endLetrecDefinitions();
        sb.endLetrecExpression();
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("step (c: k) : k");
        sb.beginLetEquationValue();
        sb.beginMatchExpression("c");
        for (Rule r : sortedRules.get(false)) {
            if (!preproc.functionRules.values().contains(r)) {
                oldConvert(r, sb, false, i++, "step");
            }
        }
        sb.addMatchEquation("_", "lookups_step c Guard.empty");
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private String mainConvert() {
        SyntaxBuilder sb = new SyntaxBuilder();

        addSortType(sb);
        addSortOrderFunc(sb);
        addKLabelType(sb);
        addKLabelOrderFunc(sb);
        addPrelude(sb);
        addPrintSortString(sb);
        addPrintKLabel(sb);
        addMidlude(sb);
        addRules(sb);
        addPostlude(sb);

        return sb.toString();
    }

    private boolean hasLookups(Rule r) {
        class Holder { boolean b; }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                h.b |= isLookupKLabel(k);
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.b;
    }

    private void outputAnnotate(Rule r, SyntaxBuilder sb) {
        sb.beginMultilineComment();
        sb.append(" rule ");
        sb.append(ToKast.apply(r.body()));
        sb.append(" requires ");
        sb.append(ToKast.apply(r.requires()));
        sb.append(" ensures ");
        sb.append(ToKast.apply(r.ensures()));
        sb.append(" ");
        sb.append(r.att().toString());
        sb.endMultilineComment();
        sb.addNewline();
    }

    private void unhandledOldConvert(Rule r, SyntaxBuilder sb, boolean function, int ruleNum, String functionName) throws KEMException {
        if(annotateOutput) { outputAnnotate(r, sb); }

        sb.append("| ");

        K left     = RewriteToTop.toLeft(r.body());
        K right    = RewriteToTop.toRight(r.body());
        K requires = r.requires();

        SetMultimap<KVariable, String> vars = HashMultimap.create();
        Visitor visitor = oldConvert(sb, false, vars, false);

        if(function) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            visitor.apply(kapp.klist().items(), true);
        } else {
            visitor.apply(left);
        }

        String result = oldConvert(vars);

        if(hasLookups(r)) {
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.append(Integer.toString(ruleNum));
            sb.append(") guards)");
        }

        String suffix = "";

        if(!(KSequence(BooleanUtils.TRUE).equals(requires)) || !("true".equals(result))) {
            suffix = oldConvertLookups(sb, requires, vars, functionName, ruleNum);
            sb.append(" when ");
            oldConvert(sb, true, vars, true).apply(requires);
            sb.append(" && (");
            sb.append(result);
            sb.append(")");
        }

        sb.append(" -> ");
        oldConvert(sb, true, vars, false).apply(right);
        sb.append(suffix);
        sb.addNewline();
    }

    private void oldConvert(Rule r, SyntaxBuilder sb, boolean function, int ruleNum, String functionName) {
        try {
            unhandledOldConvert(r, sb, function, ruleNum, functionName);
        } catch (KEMException e) {
            e.exception.addTraceFrame("while compiling rule at "
                                      + r.att().getOptional(Source.class).map(Object::toString).orElse("<none>")
                                      + ":"
                                      + r.att().getOptional(Location.class).map(Object::toString).orElse("<none>"));
            throw e;
        }
    }

    private static class Holder { int i; }

    private void checkApplyArity(KApply k, int arity, String funcName) throws KEMException {
        if(k.klist().size() != arity) {
            throw KEMException.internalError("Unexpected arity of " + funcName + ": " + k.klist().size(), k);
        }
    }

    private String oldConvertLookups(SyntaxBuilder sb,
                                     K requires,
                                     SetMultimap<KVariable, String> vars,
                                     String functionName,
                                     int ruleNum) {
        Deque<String> suffix = new ArrayDeque<>();
        Holder h = new Holder();
        h.i = 0;
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                String str1, str2;
                int arity;
                String functionStr;

                List<K> kitems = k.klist().items();
                String klabel = k.klabel().name();

                switch(klabel) {
                case "#match":
                    str1 = "";
                    str2 = "";
                    functionStr = "lookup";
                    arity = 2;
                    break;
                case "#setChoice":
                    str1 = "| [Set s] -> let choice = (KSet.fold (fun e result -> if result = [Bottom] then (match e with ";
                    str2 = "| _ -> [Bottom]) else result) s [Bottom]) in if choice = [Bottom] then ("
                         + functionName
                         + " c (Guard.add (GuardElt.Guard "
                         + ruleNum
                         + ") guards)) else choice";
                    functionStr = "set choice";
                    arity = 2;
                    break;
                case "#mapChoice":
                    str1 = "| [Map m] -> let choice = (KMap.fold (fun k v result -> if result = [Bottom] then (match k with ";
                    str2 = "| _ -> [Bottom]) else result) m [Bottom]) in if choice = [Bottom] then ("
                         + functionName
                         + " c (Guard.add (GuardElt.Guard "
                         + ruleNum
                         + ") guards)) else choice";
                    functionStr = "map choice";
                    arity = 2;
                    break;
                default: return super.apply(k);
                }

                checkApplyArity(k, arity, functionStr);

                K fstKLabel = kitems.get(0);
                K sndKLabel = kitems.get(1);

                sb.append(" -> (match ");
                oldConvert(sb, true, vars, false).apply(sndKLabel);
                sb.append(" with ");
                sb.addNewline();
                sb.append(str1);
                oldConvert(sb, false, vars, false).apply(fstKLabel);
                suffix.add("| _ -> (" + functionName + " c (Guard.add (GuardElt.Guard " + ruleNum + ") guards)))");
                suffix.add(str2);
                h.i++;
                return super.apply(k);
            }
        }.apply(requires);

        SyntaxBuilder sb2 = new SyntaxBuilder();
        while(!suffix.isEmpty()) {
            sb2.append(suffix.pollLast());
        }
        return sb2.toString();
    }

    private static String oldConvert(SetMultimap<KVariable, String> vars) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for (Collection<String> nonLinearVars : vars.asMap().values()) {
            if (nonLinearVars.size() < 2) {
                continue;
            }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                sb.append("(eq ");
                sb.append(last);
                sb.append(" ");
                sb.append(next);
                sb.append(")");
                last = next;
                sb.append(" && ");
            }
        }
        sb.append("true");
        return sb.toString();
    }

    private void applyVarRhs(KVariable v, SyntaxBuilder sb, SetMultimap<KVariable, String> vars) {
        sb.append(vars.get(v).iterator().next());
    }

    private void applyVarLhs(KVariable k, SyntaxBuilder sb, SetMultimap<KVariable, String> vars) {
        String varName = encodeStringToVariable(k.name());
        vars.put(k, varName);
        Sort s = Sort(k.att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
        if (preproc.sortAttributesFor.contains(s)) {
            String hook = preproc.sortAttributesFor.apply(s).<String>getOptional("hook").orElse("");
            if (sortHooks.containsKey(hook)) {
                sb.append("(");
                sb.append(s.name());
                sb.append(" _");
                sb.append(" as ");
                sb.append(varName);
                sb.append(")");
                return;
            }
        }
        sb.append(varName);
    }

    private Visitor oldConvert(SyntaxBuilder sb, boolean rhs, SetMultimap<KVariable, String> vars, boolean useNativeBooleanExp) {
        return new Visitor(sb, rhs, vars, useNativeBooleanExp);
    }

    private class Visitor extends VisitKORE {
        private final SyntaxBuilder sb;
        private final boolean rhs;
        private final SetMultimap<KVariable, String> vars;
        private final boolean useNativeBooleanExp;

        public Visitor(SyntaxBuilder sb, boolean rhs, SetMultimap<KVariable, String> vars, boolean useNativeBooleanExp) {
            this.sb = sb;
            this.rhs = rhs;
            this.vars = vars;
            this.useNativeBooleanExp = useNativeBooleanExp;
            this.inBooleanExp = useNativeBooleanExp;
        }

        private boolean inBooleanExp;

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
            } else if (preproc.functionSet.contains(k.klabel())) {
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
            String hook = preproc.attributesFor.apply(k.klabel()).<String>getOptional(Attribute.HOOK_KEY).orElse("");
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
            } else if (preproc.collectionFor.contains(k.klabel()) && !rhs) {
                applyKLabel(k);
                sb.append(" :: []");
            } else {
                if (preproc.attributesFor.apply(k.klabel()).contains(Attribute.PREDICATE_KEY)) {
                    Sort s = Sort(preproc.attributesFor.apply(k.klabel()).<String>get(Attribute.PREDICATE_KEY).get());
                    if (preproc.sortAttributesFor.contains(s)) {
                        String hook2 = preproc.sortAttributesFor.apply(s).<String>getOptional("hook").orElse("");
                        if (sortHooks.containsKey(hook2) && k.klist().items().size() == 1) {
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
                    }
                    if (s.equals(Sorts.KItem()) && k.klist().items().size() == 1) {
                        if (k.klist().items().get(0) instanceof KSequence) {
                            KSequence item = (KSequence) k.klist().items().get(0);
                            if (item.items().size() == 1) {
                                apply(BooleanUtils.TRUE);
                                return;
                            }
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
            if (preproc.sortAttributesFor.contains(k.sort())) {
                String hook = preproc.sortAttributesFor.apply(k.sort()).<String>getOptional("hook").orElse("");
                if (sortHooks.containsKey(hook)) {
                    sb.append(sortHooks.get(hook).apply(k.s()));
                    return null;
                }
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

        private void apply(List<K> items, boolean klist) {
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
                    || (item instanceof KApply && preproc.functionSet.contains(((KApply) item).klabel())));
        }

        private void apply(Sort sort) {
            sb.append(encodeStringToIdentifier(sort));
        }

        public void apply(KLabel klabel) {
            if (klabel instanceof KVariable) {
                apply((KVariable) klabel);
            } else {
                sb.append(encodeStringToIdentifier(klabel));
            }
        }
    }

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match") || k.klabel().name().equals("#mapChoice") || k.klabel().name().equals("#setChoice");
    }
}
