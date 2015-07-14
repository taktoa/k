// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Lists;
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
import java.util.Set;
import java.util.function.Function;

import static org.kframework.kore.KORE.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Main class for converting KORE to functional code
 *
 * @author Remy Goldschmidt
 */
public class DefinitionToFunc {
    /** Flag that determines whether or not we annotate output OCaml with rules */
    public static final boolean annotateOutput = true;

    public boolean debug = true;

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

    /**
     * Convert a {@link CompiledDefinition} to an OCaml string
     */
    public String convert(CompiledDefinition def) {
        preproc = new PreprocessedKORE(def, kem, files, globalOptions, kompileOptions);
        SyntaxBuilder sb = langDefToFunc(preproc);

//        List<String> tmp = sb.pretty();
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//
//        for(String s : tmp) {
//            outprintfln("%s", s);
//        }
//
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//        outprintfln("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");

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

    private SyntaxBuilder runtimeCodeToFunc(K k, int depth) {
        SyntaxBuilder sb = new SyntaxBuilder();
        FuncVisitor convVisitor = oldConvert(preproc,
                                             true,
                                             HashMultimap.create(),
                                             false);
        sb.addImport("Def");
        sb.addImport("K");
        sb.beginLetDeclaration();
        sb.beginLetDefinitions();
        String runFmt = "print_string(print_k(try(run(%s) (%s)) with Stuck c' -> c'))";
        sb.addLetEquation(newsb("_"),
                          newsbf(runFmt,
                                 convVisitor.apply(preproc.runtimeProcess(k)),
                                 depth));
        sb.endLetDefinitions();
        sb.endLetDeclaration();
        outprintfln("DBG: runtime # of parens: %d", sb.getNumParens());
        return sb;
    }

    private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
        return mainConvert(ppk);
    }

    private Function<String, String> wrapPrint(String pfx) {
        return x -> pfx + encodeStringToAlphanumeric(x);
    }

    private SyntaxBuilder addSimpleFunc(Collection<String> pats,
                                        Collection<String> vals,
                                        String args,
                                        String outType,
                                        String funcName,
                                        String matchVal) {
        List<String> pl = pats.stream().collect(toList());
        List<String> vl = vals.stream().collect(toList());
        return
            newsb()
            .addGlobalLet(newsbf("%s(%s) : %s",
                                 funcName,
                                 args,
                                 outType),
                          newsb().addMatch(newsb().addValue(matchVal),
                                           pl,
                                           vl));

    }

    private SyntaxBuilder addSimpleFunc(Collection<String> pats,
                                        Collection<String> vals,
                                        String inType,
                                        String outType,
                                        String funcName) {
        String varName = String.valueOf(inType.charAt(0));
        String arg = String.format("%s: %s", varName, inType);
        return addSimpleFunc(pats, vals, arg, outType, funcName, varName);
    }

    private <T> SyntaxBuilder addOrderFunc(Collection<T> elems,
                                           Function<T, String> print,
                                           String pfx,
                                           String tyName) {
        String fnName = String.format("order_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(print)
                                 .map(wrapPrint(pfx))
                                 .collect(toList());
        List<String> vals = rangeInclusive(pats.size()).stream()
                                                       .map(x -> Integer.toString(x))
                                                       .collect(toList());

        return addSimpleFunc(pats, vals, tyName, "int", fnName);
    }

    private <T> SyntaxBuilder addPrintFunc(Collection<T> elems,
                                           Function<T, String> patPrint,
                                           Function<T, String> valPrint,
                                           String pfx,
                                           String tyName) {
        String fnName = String.format("print_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(patPrint)
                                 .map(wrapPrint(pfx))
                                 .collect(toList());

        List<String> vals = elems.stream()
                                 .map(valPrint.andThen(StringUtil::enquoteCString))
                                 .collect(toList());

        return addSimpleFunc(pats, vals, tyName, "string", fnName);
    }

    private SyntaxBuilder addType(Collection<String> cons, String tyName) {
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition(tyName);
        for(String c : cons) {
            sb.addConstructor(newsb().addConstructorName(c));
        }
        sb.endTypeDefinition();
        return sb;
    }

    private <T> SyntaxBuilder addEnumType(Collection<T> toEnum,
                                          Function<T, String> print,
                                          String pfx,
                                          String tyName) {
        List<String> cons = toEnum.stream()
                                  .map(print)
                                  .map(wrapPrint(pfx))
                                  .collect(toList());
        return addType(cons, tyName);
    }


    private void addSortType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginTypeDefinition("sort");
        for(Sort s : ppk.definedSorts) {
            sb.addConstructor(newsb()
                              .addConstructorName(encodeStringToIdentifier(s)));
        }
        if(! ppk.definedSorts.contains(Sorts.String())) {
            sb.addConstructor(newsb()
                              .addConstructorName("SortString"));
        }
        sb.endTypeDefinition();
    }

    private void addKLabelType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addEnumType(ppk.definedKLabels,
                              x -> x.name(),
                              "Lbl",
                              "klabel"));
    }

    private void addSortOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedSorts,
                               x -> x.name(),
                               "Sort",
                               "sort"));
    }

    private void addKLabelOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedKLabels,
                               x -> x.name(),
                               "Lbl",
                               "klabel"));
    }

    private void addPrintSort(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedSorts,
                               x -> x.name(),
                               x -> x.name(),
                               "Sort",
                               "sort"));
    }

    private void addPrintKLabel(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedKLabels,
                               x -> x.name(),
                               x -> ToKast.apply(x),
                               "Lbl",
                               "klabel"));
    }

    private void addFunctionMatch(String functionName,
                                  KLabel functionLabel,
                                  PreprocessedKORE ppk,
                                  SyntaxBuilder sb) {
        String hook = ppk.attrLabels
                         .get(Attribute.HOOK_KEY)
                         .getOrDefault(functionLabel, "");
        boolean isHook = OCamlIncludes.hooks
                                      .containsKey(hook);
        boolean isPred = OCamlIncludes.predicateRules
                                      .containsKey(functionLabel.name());
        Collection<Rule> rules = ppk.functionRulesOrdered
                                    .getOrDefault(functionLabel,
                                                  newArrayList());

        if(!isHook && !hook.isEmpty()) {
            kem.registerCompilerWarning("missing entry for hook " + hook);
        }

        sb.beginMatchExpression(newsbv("c"));

        if(isHook) {
            sb.addMatchEquation(newsb(OCamlIncludes.hooks.get(hook)));
        }

        if(isPred) {
            sb.addMatchEquation(newsb(OCamlIncludes.predicateRules
                                                   .get(functionLabel.name())));
        }

        int i = 0;
        for(Rule r : rules) {
            oldConvert(ppk, r, sb, true, i++, functionName);
        }

        sb.addMatchEquation(newsbv("_"),
                            newsbv("raise (Stuck [KApply(lbl, c)])"));
        sb.endMatchExpression();
    }

    private void addFunctionEquation(KLabel functionLabel,
                                     PreprocessedKORE ppk,
                                     SyntaxBuilder sb) {
        String functionName = encodeStringToFunction(functionLabel.name());

        sb.beginLetrecEquation();
        sb.addLetrecEquationName(newsb()
                                 .addValue(functionName)
                                 .addSpace()
                                 .addValue("(c: k list)")
                                 .addSpace()
                                 .addValue("(guards: Guard.t)")
                                 .addSpace()
                                 .addKeyword(":")
                                 .addSpace()
                                 .addValue("k"));
        sb.beginLetrecEquationValue();
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.addLetEquation(newsb("lbl"),
                          newsb(encodeStringToIdentifier(functionLabel)));
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
        sb.addLetrecEquationName(newsb()
                                 .addValue("freshFunction")
                                 .addSpace()
                                 .addValue("(sort: string)")
                                 .addSpace()
                                 .addValue("(counter: Z.t)")
                                 .addSpace()
                                 .addKeyword(":")
                                 .addSpace()
                                 .addValue("k"));
        sb.beginLetrecEquationValue();

        sb.beginMatchExpression(newsb("sort"));
        for(Sort sort : ppk.freshFunctionFor.keySet()) {
            KLabel freshFunction = ppk.freshFunctionFor.get(sort);
            sb.addMatchEquation(newsbf("\"%s\"",
                                       sort.name()),
                                newsbf("(%s ([Int counter] :: []) Guard.empty)",
                                       encodeStringToFunction(freshFunction.name())));
        }
        sb.endMatchExpression();

        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
    }

    private void addEval(Set<KLabel> labels,
                         PreprocessedKORE ppk,
                         SyntaxBuilder sb) {
        sb.beginLetrecEquation();
        sb.addLetrecEquationName(newsb("eval (c: kitem) : k"));
        sb.beginLetrecEquationValue();

        sb.beginMatchExpression(newsb("c"));

        sb.beginMatchEquation();
        sb.addMatchEquationPattern(newsb()
                                   .addApplication("KApply",
                                                   newsb("(lbl, kl)")));
        sb.beginMatchEquationValue();
        sb.beginMatchExpression(newsb("lbl"));
        for(KLabel label : labels) {
            SyntaxBuilder valSB =
                newsb().addApplication(encodeStringToFunction(label.name()),
                                       newsb("kl"),
                                       newsb("Guard.empty"));
            sb.addMatchEquation(newsb(encodeStringToIdentifier(label)),
                                valSB);
        }
        sb.endMatchExpression();
        sb.endMatchEquationValue();

        sb.addMatchEquation(newsbv("_"),
                            newsbv("[c]"));

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
            sb.beginLetrecDeclaration();
            sb.beginLetrecDefinitions();
            for(KLabel functionLabel : component) {
                if(inLetrec) { sb.addLetrecEquationSeparator(); }
                addFunctionEquation(functionLabel, ppk, sb);
                inLetrec = true;
            }
            sb.endLetrecDefinitions();
            sb.endLetrecDeclaration();
        }

        sb.beginLetrecDeclaration();
        sb.beginLetrecDefinitions();
        addFreshFunction(ppk, sb);
        sb.addLetrecEquationSeparator();
        addEval(funcAndAny, ppk, sb);
        sb.endLetrecDefinitions();
        sb.endLetrecDeclaration();
    }

    private void addSteps(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetrecDeclaration();
        sb.beginLetrecDefinitions();
        sb.beginLetrecEquation();
        sb.addLetrecEquationName(newsb("lookups_step (c: k) (guards: Guard.t) : k"));
        sb.beginLetrecEquationValue();
        sb.beginMatchExpression(newsb("c"));
        int i = 0;
        for(Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if(cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "lookups_step");
            }
        }

        sb.addMatchEquation(newsbv("_"),
                            newsb()
                            .beginApplication()
                            .addFunction("raise")
                            .beginArgument()
                            .addApplication("Stuck", newsb("c"))
                            .endArgument()
                            .endApplication());
        sb.endMatchExpression();
        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
        sb.endLetrecDefinitions();
        sb.endLetrecDeclaration();


        sb.beginLetDeclaration();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName(newsb("step (c: k) : k"));
        sb.beginLetEquationValue();
        sb.beginMatchExpression(newsb("c"));
        for(Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if(!cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "step");
            }
        }
        sb.addMatchEquation(newsb("_"),
                            newsb("lookups_step c Guard.empty"));
        sb.endMatchExpression();
        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetDeclaration();
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

        sb.addNewline();
        sb.addKeyword("|");
        sb.addSpace();

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

        SyntaxBuilder result = oldConvert(vars);

        if(ppk.indexedRules.get(r).contains("lookup")) {
            sb.addSpace();
            sb.addKeyword("when");
            sb.addSpace();

            sb.addKeyword("not");
            sb.addSpace();
            sb.beginApplication();
            sb.addFunction("Guard.mem");

            sb.beginArgument();
            sb.addApplication("GuardElt.Guard", newsbf("%d", ruleNum));
            sb.endArgument();

            sb.addArgument(newsb("guards"));

            sb.endApplication();
        }

        SyntaxBuilder suffix = newsb();

        if(   !(KSequence(BooleanUtils.TRUE).equals(requires))
           || !("true".equals(result.toString()))) {
            suffix = oldConvertLookups(ppk, sb, requires, vars,
                                       functionName, ruleNum);
            sb.addSpace();
            sb.addKeyword("when");
            sb.addSpace();
            sb.append(oldConvert(ppk, true, vars, true).apply(requires));
            sb.addSpace();
            sb.addKeyword("&&");
            sb.addSpace();
            sb.beginParenthesis();
            sb.append(result);
            sb.endParenthesis();
        }

        sb.addSpace();
        sb.addKeyword("->");
        sb.addSpace();
        sb.append(oldConvert(ppk, true, vars, false)
                  .apply(right));
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
            String src = r.att()
                          .getOptional(Source.class)
                          .map(Object::toString)
                          .orElse("<none>");
            String loc = r.att()
                          .getOptional(Location.class)
                          .map(Object::toString)
                          .orElse("<none>");
            e.exception.addTraceFrame(String.format("while compiling rule at %s: %s",
                                                    src,
                                                    loc));
            throw e;
        }
    }

    private void checkApplyArity(KApply k,
                                 int arity,
                                 String funcName) throws KEMException {
        if(k.klist().size() != arity) {
            throw kemInternalErrorF(k,
                                    "Unexpected arity of %s: %s",
                                    funcName,
                                    k.klist().size());
        }
    }

    // TODO(remy): this needs refactoring very badly
    private SyntaxBuilder oldConvertLookups(PreprocessedKORE ppk,
                                            SyntaxBuilder sb,
                                            K requires,
                                            SetMultimap<KVariable, String> vars,
                                            String functionName,
                                            int ruleNum) {
        int oldParens = sb.getNumParens();

        Deque<SyntaxBuilder> suffix = new ArrayDeque<>();
        class Holder { int i; }
        Holder h = new Holder();
        h.i = 0;

        SyntaxBuilder res = new SyntaxBuilder();

        new VisitKORE() {
            private SyntaxBuilder sb1 = new SyntaxBuilder();
            private SyntaxBuilder sb2 = new SyntaxBuilder();
            private final SyntaxBuilder wildcard = newsbv("_");
            private final SyntaxBuilder bot = newsbv("[Bottom]");
            private final SyntaxBuilder rnsb = newsbv(Integer.toString(ruleNum));
            private final String guardCon = "GuardElt.Guard";
            private final String guardAdd = "Guard.add";
            private final String foldSet = "KSet.fold";
            private final String foldMap = "KMap.fold";
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

//                res.beginMultilineComment();
//                res.addValue("DBG: begin");
//                res.endMultilineComment();
                res.endMatchEquationPattern();
                res.beginMatchEquationValue();
                res.beginMatchExpression(oldConvert(ppk, true,
                                                    vars, false)
                                         .apply(sndKLabel));
                res.append(sb1);
                res.append(oldConvert(ppk, false,
                                      vars, false)
                           .apply(fstKLabel));
                SyntaxBuilder sb3 = new SyntaxBuilder();
                String fmt = "(%s c (Guard.add (GuardElt.Guard %d) guards))";
                String tmp = String.format(fmt,
                                           functionName,
                                           ruleNum);
                sb3.addMatchEquation(wildcard,
                                     newsb(tmp));
                sb3.endMatchExpression();
                sb3.endMatchEquationValue();
                sb3.endMatchEquation();
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
                sb1 = newsb()
                    .beginMatchEquation()
                    .addMatchEquationPattern(newsbv("[Set s]"))
                    .beginMatchEquationValue()
                    .beginLetExpression()
                    .beginLetDefinitions()
                    .beginLetEquation()
                    .addLetEquationName(newsbv("choice"))
                    .beginLetEquationValue()
                    .beginApplication()
                    .addFunction(foldSet)
                    .beginArgument()
                    .beginLambda("e", "result")
                    .addConditionalIf()
                    .addEqualityTest(newsbv("result"), bot)
                    .addConditionalThen()
                    .beginMatchExpression(newsbv("e"));
                // endMatchExpression

                sb2 = newsb()
                    .addMatchEquation(wildcard, bot)
                    .endMatchExpression()
                    .addConditionalElse()
                    .addValue("result")
                    .endLambda()
                    .endArgument()

                    .addArgument(newsbv("s"))
                    .addArgument(bot)

                    .endApplication()
                    .endLetEquationValue()
                    .endLetEquation()
                    .endLetDefinitions()
                    .beginLetScope()

                    .addConditionalIf()

                    .addEqualityTest(newsbv("choice"), bot)

                    .addConditionalThen()

                    .beginApplication()
                    .addFunction(functionName)
                    .addArgument(newsbv("c"))
                    .beginArgument()
                    .beginApplication()
                    .addFunction(guardAdd)
                    .beginArgument()
                    .addApplication(guardCon, rnsb)
                    .endArgument()
                    .addArgument(newsbv("guards"))
                    .endApplication()
                    .endArgument()
                    .endApplication()

                    .addConditionalElse()

                    .addValue("choice")
                    .endLetScope()
                    .endLetExpression()
                    .endMatchEquationValue()
                    .endMatchEquation();

                functionStr = "set choice";
                arity = 2;
            }

            private void isMapChoice() {
//                SyntaxBuilder testsb1 = newsb();
//                testsb1
//                    .addNewline()
//                    .beginMatchEquation()
//                    .addMatchEquationPattern(newsbv("[Map m]"))
//                    .beginMatchEquationValue()
//                    .beginLetExpression()
//                    .beginLetDefinitions()
//                    .beginLetEquation()
//                    .addLetEquationName(newsbv("choice"))
//                    .beginLetEquationValue()
//                    .beginApplication()
//                    .addFunction(foldMap)
//                    .beginArgument()
//                    .beginLambda("k", "v", "result")
//                    .addConditionalIf()
//                    .addEqualityTest(newsbv("result"), bot)
//                    .addConditionalThen()
//                    .beginMatchExpression(newsbv("k"));

                sb1.append("\n| [Map m] -> let choice = (KMap.fold (fun k v result -> if result = [Bottom] then (match k with ");



//                SyntaxBuilder guardSB =
//                    newsb().addApplication(guardAdd,
//                                           newsb().addApplication(guardCon, rnsb),
//                                           newsbv("guards"));
//
//                SyntaxBuilder testsb2 = newsb();
//                testsb2
//                    .addMatchEquation(wildcard, bot)
//                    .endMatchExpression()
//                    .addConditionalElse()
//                    .addValue("result")
//                    .endLambda()
//                    .endArgument()
//                    .addArgument(newsbv("m"))
//                    .addArgument(bot)
//                    .endApplication()
//                    .endLetEquationValue()
//                    .endLetEquation()
//                    .endLetDefinitions()
//                    .beginLetScope()
//                    .addConditionalIf()
//                    .addEqualityTest(newsbv("choice"), bot)
//                    .addConditionalThen()
//                    .addApplication(functionName,
//                                    newsbv("c"),
//                                    newsb().addArgument(guardSB))
//                    .addConditionalElse()
//                    .addValue("choice")
//                    .endLetScope()
//                    .endLetExpression()
//                    .endMatchEquationValue()
//                    .endMatchEquation();

                sb2.appendf("\n| _ -> [Bottom]) else result) m [Bottom])" +
                            " in if choice = [Bottom] " +
                            "then (%s c (Guard.add (GuardElt.Guard %d) guards)) " +
                            "else choice",
                            functionName,
                            ruleNum);

                functionStr = "map choice";
                arity = 2;
            }
        }.apply(requires);

        sb.append(res);

        SyntaxBuilder suffSB = new SyntaxBuilder();
        while(!suffix.isEmpty()) { suffSB.append(suffix.pollLast()); }

        int newParens = sb.getNumParens();

        int sufParens = suffSB.getNumParens();

        if((newParens + sufParens) != oldParens) {
            outprintfln("DBG: ");
            outprintfln("DBG: ");
            outprintfln("DBG: ");
            outprintfln("DBG:     ERROR: %d != %d",
                        newParens + sufParens,
                        oldParens);
            outprintfln("DBG: oldParens: %d", oldParens);
            outprintfln("DBG: newParens: %d", newParens);
            outprintfln("DBG: sufParens: %d", sufParens);
            outprintfln("DBG:    Rule #: %d", ruleNum);
            outprintfln("DBG: Func name: %s", functionName);
        }

        return suffSB;
    }

    private static SyntaxBuilder oldConvert(SetMultimap<KVariable, String> vars) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for(Collection<String> nonLinearVars : vars.asMap().values()) {
            if(nonLinearVars.size() < 2) { continue; }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                sb.addApplication("eq", newsb(last), newsb(next));
                last = next;
                sb.addSpace();
                sb.addKeyword("&&");
                sb.addSpace();
            }
        }
        sb.addValue("true");
        return sb;
    }

    private FuncVisitor oldConvert(PreprocessedKORE ppk,
                                   boolean rhs,
                                   SetMultimap<KVariable, String> vars,
                                   boolean useNativeBooleanExp) {
        return new FuncVisitor(ppk, rhs, vars, useNativeBooleanExp);
    }
}
