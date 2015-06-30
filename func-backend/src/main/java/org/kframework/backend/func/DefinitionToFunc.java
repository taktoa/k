// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KSequence;
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
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Main class for converting KORE to functional code
 *
 * @author Remy Goldschmidt
 */
public class DefinitionToFunc {
    /** Flag that determines whether or not we annotate output OCaml with rules */
    public static final boolean annotateOutput = false;

    private final KExceptionManager kem;
    private final FileUtil files;
    private final GlobalOptions globalOptions;
    private final KompileOptions kompileOptions;

    private PreprocessedKORE preproc;

    /**
     * Constructor for DefinitionToFunc
     */
    public DefinitionToFunc(KExceptionManager kem,
                            FileUtil files,
                            GlobalOptions globalOptions,
                            KompileOptions kompileOptions) {
        this.kem = kem;
        this.files = files;
        this.globalOptions = globalOptions;
        this.kompileOptions = kompileOptions;
    }

    private SyntaxBuilder runtimeCodeToFunc(K k, int depth) {
        SyntaxBuilder sb = new SyntaxBuilder();
        FuncVisitor convVisitor = oldConvert(preproc, true, HashMultimap.create(), false);
        sb.addImport("Def");
        sb.addImport("K");
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.addLetEquation("_",
                          String.format("print_string(print_k(try(run(%s) (%s)) with Stuck c' -> c'))",
                                        convVisitor.apply(preproc.runtimeProcess(k)),
                                        depth));
        sb.endLetDefinitions();
        sb.endLetExpression();
        return sb;
    }

    private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
        return mainConvert(ppk);
    }

    /**
     * Convert a {@link CompiledDefinition} to an OCaml string
     */
    public String convert(CompiledDefinition def) {
        preproc = new PreprocessedKORE(def, kem, files, globalOptions, kompileOptions);
        SyntaxBuilder sb = langDefToFunc(preproc);
        List<String> tmp = sb.pretty();

        for(String s : tmp) {
            System.out.println(s);
        }

        if(1 == 1) {
            throw kemCriticalErrorF("lol error");
        }
        return sb.toString();
    }

    /**
     * Convert KORE to an OCaml string that runs against the
     * code generated from the {@link CompiledDefinition}, up
     * to a certain evaluation depth.
     */
    public String convert(K k, int depth) {
        return runtimeCodeToFunc(k, depth).toString();
    }

    private Function<String, String> wrapPrint(String pfx) {
        return x -> pfx + encodeStringToAlphanumeric(x);
    }

    private String addSimpleFunc(Collection<String> pats,
                                 Collection<String> vals,
                                 String args,
                                 String outType,
                                 String funcName,
                                 String matchVal) {
        SyntaxBuilder matchSB = new SyntaxBuilder();
        matchSB.addMatch(matchVal,
                         pats.stream().collect(Collectors.toList()),
                         vals.stream().collect(Collectors.toList()));

        String letName = String.format("%s(%s) : %s",
                                       funcName, args, outType);

        SyntaxBuilder output = new SyntaxBuilder();
        output.addGlobalLet(letName, matchSB.toString());

        return output.toString();
    }

    private String addSimpleFunc(Collection<String> pats,
                                 Collection<String> vals,
                                 String inType,
                                 String outType,
                                 String funcName) {
        String varName = String.valueOf(inType.charAt(0));
        String arg = String.format("%s: %s", varName, inType);
        return addSimpleFunc(pats, vals, arg, outType, funcName, varName);
    }

    private <T> String addOrderFunc(Collection<T> elems,
                                    Function<T, String> print,
                                    String pfx,
                                    String tyName) {
        String fnName = String.format("order_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(print)
                                 .map(wrapPrint(pfx))
                                 .collect(Collectors.toList());
        List<String> vals = rangeInclusive(pats.size()).stream()
                                                       .map(x -> Integer.toString(x))
                                                       .collect(Collectors.toList());

        return addSimpleFunc(pats, vals, tyName, "int", fnName);
    }

    private <T> String addPrintFunc(Collection<T> elems,
                                    Function<T, String> patPrint,
                                    Function<T, String> valPrint,
                                    String pfx,
                                    String tyName) {
        String fnName = String.format("print_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(patPrint)
                                 .map(wrapPrint(pfx))
                                 .collect(Collectors.toList());

        List<String> vals = elems.stream()
                                 .map(valPrint.andThen(StringUtil::enquoteCString))
                                 .collect(Collectors.toList());

        return addSimpleFunc(pats, vals, tyName, "string", fnName);
    }

    private String addType(Collection<String> cons, String tyName) {
        SyntaxBuilder sb = new SyntaxBuilder();
        sb.beginTypeDefinition(tyName);
        for(String c : cons) {
            sb.addConstructor(c);
        }
        sb.endTypeDefinition();
        return sb.toString();
    }

    private <T> String addEnumType(Collection<T> toEnum,
                                   Function<T, String> print,
                                   String pfx,
                                   String tyName) {
        List<String> cons = toEnum.stream()
                                  .map(print)
                                  .map(wrapPrint(pfx))
                                  .collect(Collectors.toList());
        return addType(cons, tyName);
    }

    private void addSortOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedSorts, x -> x.name(), "Sort", "sort"));
    }

    private void addSortType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginTypeDefinition("sort");
        for(Sort s : ppk.definedSorts) {
            sb.beginConstructor();
            sb.append(encodeStringToIdentifier(s));
            sb.endConstructor();
        }
        if(!ppk.definedSorts.contains(Sorts.String())) {
            sb.addConstructor("SortString");
        }
        sb.endTypeDefinition();
    }

    private void addKLabelType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addEnumType(ppk.definedKLabels, x -> x.name(), "Lbl", "klabel"));
    }

    private void addKLabelOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedKLabels, x -> x.name(), "Lbl", "klabel"));
    }

    private void addPrintSort(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedSorts, x -> x.name(), x -> x.name(), "Sort", "sort"));
    }

    private void addPrintKLabel(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedKLabels, x -> x.name(), x -> ToKast.apply(x), "Lbl", "klabel"));
    }

    private void addFunctionMatch(String functionName,
                                  KLabel functionLabel,
                                  PreprocessedKORE ppk,
                                  SyntaxBuilder sb) {
        String hook = ppk.attrLabels.get(Attribute.HOOK_KEY).getOrDefault(functionLabel, "");
        boolean isHook = OCamlIncludes.hooks.containsKey(hook);
        boolean isPred = OCamlIncludes.predicateRules.containsKey(functionLabel.name());
        Collection<Rule> rules = ppk.functionRulesOrdered.getOrDefault(functionLabel, new ArrayList<>());

        if(!isHook && !hook.isEmpty()) {
            kem.registerCompilerWarning("missing entry for hook " + hook);
        }

        sb.beginMatchExpression("c");

        if(isHook) {
            sb.addMatchEquation(OCamlIncludes.hooks.get(hook));
        }

        if(isPred) {
            sb.addMatchEquation(OCamlIncludes.predicateRules.get(functionLabel.name()));
        }

        int i = 0;
        for(Rule r : rules) {
            oldConvert(ppk, r, sb, true, i++, functionName);
        }

        sb.addMatchEquation("_", "raise (Stuck [KApply(lbl, c)])");
        sb.endMatchExpression();
    }

    private void addFunctionEquation(KLabel functionLabel,
                                     PreprocessedKORE ppk,
                                     SyntaxBuilder sb) {
        String functionName = encodeStringToFunction(functionLabel.name());

        sb.beginLetrecEquation();
        sb.addLetrecEquationName(String.format("%s (c: k list) (guards: Guard.t) : k",
                                               functionName));
        sb.beginLetrecEquationValue();
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.addLetEquation("lbl", encodeStringToIdentifier(functionLabel));
        sb.endLetDefinitions();

        sb.beginLetScope();
        addFunctionMatch(functionName, functionLabel, ppk, sb);
        sb.endLetScope();

        sb.endLetExpression();
        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
    }

    private void addFreshFunction(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetrecEquation();
        sb.addLetrecEquationName("freshFunction (sort: string) (counter: Z.t) : k");
        sb.beginLetrecEquationValue();

        sb.beginMatchExpression("sort");
        for(Sort sort : ppk.freshFunctionFor.keySet()) {
            KLabel freshFunction = ppk.freshFunctionFor.get(sort);
            String pat = String.format("\"%s\"", sort.name());
            String val = String.format("(%s ([Int counter] :: []) Guard.empty)",
                                       encodeStringToFunction(freshFunction.name()));
            sb.addMatchEquation(pat, val);
        }
        sb.endMatchExpression();

        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
    }

    private void addEval(Set<KLabel> labels,
                         PreprocessedKORE ppk,
                         SyntaxBuilder sb) {
        sb.beginLetrecEquation();
        sb.addLetrecEquationName("eval (c: kitem) : k");
        sb.beginLetrecEquationValue();

        sb.beginMatchExpression("c");

        sb.beginMatchEquation();
        sb.addMatchEquationPattern("KApply(lbl, kl)");
        sb.beginMatchEquationValue();
        sb.beginMatchExpression("lbl");
        for(KLabel label : labels) {
            String pat = encodeStringToIdentifier(label);
            String val = String.format("%s kl Guard.empty",
                                       encodeStringToFunction(label.name()));
            sb.addMatchEquation(pat, val);
        }
        sb.endMatchExpression();
        sb.endMatchEquationValue();

        sb.addMatchEquation("_", "[c]");

        sb.endMatchExpression();

        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
    }

    private void addFunctions(PreprocessedKORE ppk, SyntaxBuilder sb) {
        Set<KLabel> functions = ppk.functionSet;
        Set<KLabel> anywheres = ppk.anywhereSet;

        Set<KLabel> funcAndAny = Sets.union(functions, anywheres);

        for(List<KLabel> component : ppk.functionOrder) {
            boolean inLetrec = false;
            sb.beginLetrecExpression();
            sb.beginLetrecDefinitions();
            for(KLabel functionLabel : component) {
                if(inLetrec) { sb.addLetrecEquationSeparator(); }
                addFunctionEquation(functionLabel, ppk, sb);
                inLetrec = true;
            }
            sb.endLetrecDefinitions();
            sb.endLetrecExpression();
        }

        sb.beginLetrecExpression();
        sb.beginLetrecDefinitions();
        addFreshFunction(ppk, sb);
        sb.addLetrecEquationSeparator();
        addEval(funcAndAny, ppk, sb);
        sb.endLetrecDefinitions();
        sb.endLetrecExpression();
    }

    private void addSteps(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetrecExpression();
        sb.beginLetrecDefinitions();
        sb.beginLetrecEquation();
        sb.addLetrecEquationName("lookups_step (c: k) (guards: Guard.t) : k");
        sb.beginLetrecEquationValue();
        sb.beginMatchExpression("c");
        int i = 0;
        for(Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if(cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "lookups_step");
            }
        }
        sb.addMatchEquation("_", "raise (Stuck c)");
        sb.endMatchExpression();
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
        for(Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if(!cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "step");
            }
        }
        sb.addMatchEquation("_", "lookups_step c Guard.empty");
        sb.endMatchExpression();
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private SyntaxBuilder mainConvert(PreprocessedKORE ppk) {
        SyntaxBuilder sb = new SyntaxBuilder();

        addSortType(ppk, sb);
        addSortOrderFunc(ppk, sb);
        addKLabelType(ppk, sb);
        addKLabelOrderFunc(ppk, sb);
        OCamlIncludes.addPrelude(sb);
        addPrintSort(ppk, sb);
        addPrintKLabel(ppk, sb);
        OCamlIncludes.addMidlude(sb);
        addFunctions(ppk, sb);
        addSteps(ppk, sb);
        OCamlIncludes.addPostlude(sb);

        return sb;
    }


    private void outputAnnotate(Rule r, SyntaxBuilder sb) {
        sb.beginMultilineComment();
        sb.appendf("rule %s requires %s ensures %s %s",
                   ToKast.apply(r.body()),
                   ToKast.apply(r.requires()),
                   ToKast.apply(r.ensures()),
                   r.att().toString());
        sb.endMultilineComment();
        sb.addNewline();
    }

    private void unhandledOldConvert(PreprocessedKORE ppk,
                                     Rule r,
                                     SyntaxBuilder sb,
                                     boolean function,
                                     int ruleNum,
                                     String functionName) throws KEMException {
        if(annotateOutput) { outputAnnotate(r, sb); }

        sb.append("\n| ");

        K left     = RewriteToTop.toLeft(r.body());
        K right    = RewriteToTop.toRight(r.body());
        K requires = r.requires();

        SetMultimap<KVariable, String> vars = HashMultimap.create();
        FuncVisitor visitor = oldConvert(ppk, false, vars, false);

        if(function) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            sb.append(visitor.apply(kapp.klist().items(), true));
        } else {
            sb.append(visitor.apply(left));
        }

        String result = oldConvert(vars);

        if(ppk.indexedRules.get(r).contains("lookup")) {
            sb.appendf(" when not (Guard.mem (GuardElt.Guard %s) guards)",
                       Integer.toString(ruleNum));
        }

        String suffix = "";

        if(!(KSequence(BooleanUtils.TRUE).equals(requires)) || !("true".equals(result))) {
            suffix = oldConvertLookups(ppk, sb, requires, vars, functionName, ruleNum);
            sb.appendf(" when %s && (%s)",
                       oldConvert(ppk, true, vars, true).apply(requires),
                       result);
        }

        sb.append(" -> ");
        sb.append(oldConvert(ppk, true, vars, false).apply(right));
        sb.append(suffix);
        sb.addNewline();
    }

    private void oldConvert(PreprocessedKORE ppk,
                            Rule r,
                            SyntaxBuilder sb,
                            boolean function,
                            int ruleNum,
                            String functionName) {
        try {
            unhandledOldConvert(ppk, r, sb, function, ruleNum, functionName);
        } catch (KEMException e) {
            String src = r.att().getOptional(Source.class).map(Object::toString).orElse("<none>");
            String loc = r.att().getOptional(Location.class).map(Object::toString).orElse("<none>");
            e.exception.addTraceFrame(String.format("while compiling rule at %s: %s", src, loc));
            throw e;
        }
    }


    private void checkApplyArity(KApply k, int arity, String funcName) throws KEMException {
        if(k.klist().size() != arity) {
            throw KEMException.internalError("Unexpected arity of " + funcName + ": " + k.klist().size(), k);
        }
    }

    // TODO(remy): this needs refactoring very badly
    private String oldConvertLookups(PreprocessedKORE ppk,
                                     SyntaxBuilder sb,
                                     K requires,
                                     SetMultimap<KVariable, String> vars,
                                     String functionName,
                                     int ruleNum) {
        Deque<SyntaxBuilder> suffix = new ArrayDeque<>();
        class Holder { int i; }
        Holder h = new Holder();
        h.i = 0;

        new VisitKORE() {
            private final SyntaxBuilder sb1 = new SyntaxBuilder();
            private final SyntaxBuilder sb2 = new SyntaxBuilder();
            private int arity;
            private String functionStr;

            @Override
            public Void apply(KApply k) {
                List<K> kitems = k.klist().items();
                String klabel = k.klabel().name();

                switch(klabel) {
                case "#match":
                    isMatch();
                    break;
                case "#setChoice":
                    isSetChoice();
                    break;
                case "#mapChoice":
                    isMapChoice();
                    break;
                default: return super.apply(k);
                }

                checkApplyArity(k, arity, functionStr);

                K fstKLabel = kitems.get(0);
                K sndKLabel = kitems.get(1);

                sb.append(" -> ");
                sb.beginMatchExpression(oldConvert(ppk, true, vars, false).apply(sndKLabel));
                sb.append(sb1);
                sb.append(oldConvert(ppk, false, vars, false).apply(fstKLabel));
                SyntaxBuilder sb3 = new SyntaxBuilder();
                sb3.appendf("\n| _ -> (%s c (Guard.add (GuardElt.Guard %s) guards)))",
                            functionName,
                            Integer.toString(ruleNum));
                suffix.add(sb3);
                suffix.add(sb2);
                h.i++;
                return super.apply(k);
            }

            private void isMatch() {
                functionStr = "lookup";
                arity = 2;
            }

            private void isSetChoice() {
                sb1.beginMatchEquation();
                sb1.addMatchEquationPattern("[Set s]");
                sb1.beginMatchEquationValue();
                sb1.beginLetExpression();
                sb1.beginLetEquation();
                sb1.addLetEquationName("choice");
                sb1.beginLetEquationValue();
                sb1.beginApplication();

                sb1.addFunction("KSet.fold");
                //sb1.beginArgument();
                // sb1.beginApplication();
                sb1.beginLambda("e", "result");
                sb1.addConditionalIf();
                sb1.addValue("result = [Bottom]");
                sb1.addConditionalThen();
                sb1.beginMatchExpression("e");
                // endMatchExpression

                sb2.addMatchEquation("_", "[Bottom]");
                sb2.endMatchExpression();
                sb2.addConditionalElse();
                sb2.append("result");
                sb2.endParenthesis();

                sb2.addArgument("s");
                sb2.addArgument("[Bottom]");

                sb2.endApplication();
                sb2.endLetEquationValue();
                sb2.endLetEquation();
                sb2.endLetDefinitions();
                sb2.beginLetScope();
                sb2.addConditionalIf();
                sb2.addValue("choice = [Bottom]");
                sb2.addConditionalThen();
                sb2.beginApplication();
                sb2.addFunction(functionName);
                sb2.addArgument("c");
                sb2.beginArgument();
                sb2.beginApplication();
                sb2.addFunction("Guard.add");
                sb2.beginArgument();
                sb2.addApplication("GuardElt.Guard", Integer.toString(ruleNum));
                sb2.endArgument();
                sb2.addArgument("guards");
                sb2.endApplication();
                sb2.endArgument();
                sb2.endApplication();
                sb2.addConditionalElse();
                sb2.addValue("choice");

                functionStr = "set choice";
                arity = 2;
            }

            private void isMapChoice() {
                sb1.appendf("\n| [Map m] -> let choice = (KMap.fold (fun k v result -> if result = [Bottom] then (match k with ");
                sb2.appendf("\n| _ -> [Bottom]) else result) m [Bottom]) in " +
                            "if choice = [Bottom] " +
                            "then (%s c (Guard.add (GuardElt.Guard %s) guards)) " +
                            "else choice", functionName, Integer.toString(ruleNum));
                functionStr = "map choice";
                arity = 2;
            }
        }.apply(requires);

        SyntaxBuilder suffSB = new SyntaxBuilder();
        while(!suffix.isEmpty()) {
            suffSB.append(suffix.pollLast());
        }
        return suffSB.toString();
    }

    private static String oldConvert(SetMultimap<KVariable, String> vars) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for(Collection<String> nonLinearVars : vars.asMap().values()) {
            if(nonLinearVars.size() < 2) {
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

    private FuncVisitor oldConvert(PreprocessedKORE ppk,
                                   boolean rhs,
                                   SetMultimap<KVariable, String> vars,
                                   boolean useNativeBooleanExp) {
        return new FuncVisitor(ppk, rhs, vars, useNativeBooleanExp);
    }
}
