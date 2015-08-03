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
import org.kframework.kore.KRewrite;
import org.kframework.kore.KToken;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.kore.AbstractKORETransformer;
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
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.attributes.Att;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.backend.java.kore.compile.ExpandMacros;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kil.FloatBuiltin;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.Assoc;
import org.kframework.kore.AttCompare;
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
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.DeconstructIntegerAndFloatLiterals;
import org.kframework.kore.compile.GenerateSortPredicateRules;
import org.kframework.kore.compile.LiftToKSequence;
import org.kframework.kore.compile.NormalizeVariables;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.mpfr.BigFloat;
import org.kframework.utils.StringUtil;
import org.kframework.utils.algorithms.SCCTarjan;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Function1;
import scala.Tuple3;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

/**
 * Main class for converting KORE to functional code
 *
 * @author Remy Goldschmidt
 */
public class DefinitionToFunc {

    public static final boolean ocamlopt = false;
    public static final boolean fastCompilation = true;
    public static final Pattern identChar = Pattern.compile("[A-Za-z0-9_]");

    private final KExceptionManager kem;
    private final SyntaxBuilder ocamlDef;

    private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
    private static final SyntaxBuilder wildcardSB, bottomSB, choiceSB, resultSB;

    static {
        wildcardSB = newsbv("_");
        bottomSB = newsbv("[Bottom]");
        choiceSB = newsbv("choice");
        resultSB = newsbv("result");
        setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
                                 "e", "result");
        mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
                                 "k", "v", "result");
    }

    /**
     * Constructor for DefinitionToFunc
     */
    public DefinitionToFunc(KExceptionManager kem,
                            PreprocessedKORE preproc) {
        this.kem = kem;
        this.ocamlDef = langDefToFunc(preproc);
        debugOutput(preproc);
    }

    public String genOCaml() {
        return ocamlDef.toString();
    }

    private void debugOutput(PreprocessedKORE ppk) {
        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");

        outprintfln(";; %s", ocamlDef.trackPrint()
                                     .replaceAll("\n", "\n;; "));
        outprintfln(";; Number of parens: %d", ocamlDef.getNumParens());
        outprintfln(";; Number of lines:  %d", ocamlDef.getNumLines());


        outprintfln("functionSet: %s", ppk.functionSet);
        outprintfln("anywhereSet: %s", ppk.anywhereSet);

        XMLBuilder outXML = ocamlDef.getXML();


        outprintfln("");

        try {
            outprintfln("%s", outXML.renderSExpr());
        } catch(KEMException e) {
            outprintfln(";; %s", outXML.toString()
                        .replaceAll("><", ">\n<")
                        .replaceAll("\n", "\n;; "));
        }


        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");
    }

    private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        sb.append(FuncConstants.genConstants(ppk));
        sb.append(addFunctions(ppk));
        sb.append(addSteps(ppk));
        sb.append(OCamlIncludes.postludeSB);

        return sb;
    }

    // Move to OCamlIncludes
    public static String enquoteString(String value) {
        char delimiter = '"';
        final int length = value.length();
        StringBuilder result = new StringBuilder();
        result.append(delimiter);
        for (int offset = 0, codepoint; offset < length; offset += Character.charCount(codepoint)) {
            codepoint = value.codePointAt(offset);
            if (codepoint > 0xFF) {
                throw KEMException.compilerError("Unsupported: unicode characters in strings in Ocaml backend.");
            } else if (codepoint == delimiter) {
                result.append("\\" + delimiter);
            } else if (codepoint == '\\') {
                result.append("\\\\");
            } else if (codepoint == '\n') {
                result.append("\\n");
            } else if (codepoint == '\t') {
                result.append("\\t");
            } else if (codepoint == '\r') {
                result.append("\\r");
            } else if (codepoint == '\b') {
                result.append("\\b");
            } else if (codepoint >= 32 && codepoint < 127) {
                result.append((char)codepoint);
            } else if (codepoint <= 0xff) {
                result.append("\\");
                result.append(String.format("%03d", codepoint));
            }
        }
        result.append(delimiter);
        return result.toString();
    }

    public String execute(K k, int depth, String outFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("run")
            .addArgument(newsbv(convertRuntime(k)))
            .addArgument(newsbv(Integer.toString(depth)))
            .endApplication();
        return genRuntime(newsbApp("output_string",
                                   newsbn("out"),
                                   newsbApp("print_k",
                                            newsb()
                                            .beginTry()
                                            .addTryValue(tryValueSB)
                                            .beginTryEquations()
                                            .addTryEquation(newsbn("Stuck c'"),
                                                            newsbn("c'"))
                                            .endTryEquations()
                                            .endTry())),
                          outFile);
    }

    public String match(K k, Rule r, String outFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("print_subst")
            .addArgument("file1")
            .beginArgument()
            .beginApplication()
            .addFunction("try_match")
            .beginArgument()
            .addValue(convertRuntime(k))
            .endArgument()
            .endApplication()
            .endArgument()
            .endApplication();
        SyntaxBuilder printOutSB = newsbApp("output_string",
                                            newsbn("file1"),
                                            newsbv(enquoteString("0\n")));
        return genRuntime(newsb()
                          .beginTry()
                          .addTryValue(tryValueSB)
                          .beginTryEquations()
                          .addTryEquation(newsbn("Stuck c"), printOutSB)
                          .endTryEquations()
                          .endTry(),
                          outFile);
    }

    public String executeAndMatch(K k,
                                  int depth,
                                  Rule r,
                                  String outFile,
                                  String substFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("print_subst")
            .addArgument("file2")
            .beginArgument()
            .beginApplication()
            .addFunction("try_match")
            .beginArgument()
            .beginLetExpression()
            .beginLetEquations()
            .addLetEquation(newsbn("res"),
                            newsbApp("run",
                                     newsbv(convertRuntime(k)),
                                     newsbv(depth)))
            .endLetEquations()
            .beginLetScope()
            .addSequence(newsbApp("output_string",
                                  newsbn("file1"),
                                  newsbApp("print_k", newsbn("res"))),
                         newsbn("res"))
            .endLetScope()
            .endLetExpression()
            .endArgument()
            .endApplication()
            .endArgument()
            .endApplication();
        SyntaxBuilder printOutSB   = newsbApp("output_string",
                                              newsbn("file1")
                                              newsbApp("print_k", newsbn("c")));
        SyntaxBuilder printSubstSB = newsbApp("output_string",
                                              newsbn("file2"),
                                              newsbv(enquoteString("0\n")));
        return genRuntime(newsb()
                          .beginTry()
                          .addTryValue(tryValueSB)
                          .beginTryEquations()
                          .addTryEquation(newsbn("Stuck c"),
                                          newsb().addSequence(printOutSB,
                                                              printSubstSB))
                          .endTryEquations()
                          .endTry(),
                          outFile, substFile);
    }

    private SyntaxBuilder genRuntime(SyntaxBuilder body,
                                     String... paths) {
        return newsb()
            .append(genImports())
            .append(genTryMatch(r))
            .append(genFileDefs(paths))
            .append(genConfig())
            .append(runCode(body));
    }

    private SyntaxBuilder runCode(SyntaxBuilder code) {
        return newsb().addGlobalLet(wildcardSB, code);
    }

    private SyntaxBuilder genConfig() {
        return newsb().addGlobalLet(newsb().addName("config"), bottomSB);
    }

    private SyntaxBuilder genFileDefs(String... paths) {
        SyntaxBuilder sb;
        int i = 0;
        for(String path : paths) {
            sb.addGlobalLet(newsb().addName(String.format("file%d", i++)),
                            newsb().addApplication("open_out",
                                                   newsbv(enquoteString(path))));
        }
        return sb;
    }

    private SyntaxBuilder genTryMatch() {
        return newsb()
            .append("let try_match (c: k) : k Subst.t =")
            .append("let config = c in")
            .append("match c with ")
            .append("\n")
            .append(convertFunction(Collections.singletonList(convert(r)),
                                    "try_match", RuleType.PATTERN))
            .append("| _ -> raise(Stuck c)")
            .append("\n");
    }

    private SyntaxBuilder genImports() {
        return newsb()
            .addImport("Prelude")
            .addImport("Constants")
            .addImport("Prelude.K")
            .addImport("Gmp")
            .addImport("Def");
    }

    private String convertRuntime(K k) {
        return convert(true,
                       new VarInfo(),
                       false,
                       false).apply(preproc.runtimeProcess(k))
    }

    // Move to FuncConstants
    public String constants() {
        StringBuilder sb = new StringBuilder();
        sb.append("type sort = \n");
        if (fastCompilation) {
            sb.append("Sort of string\n");
        } else {
            for (Sort s : iterable(mainModule.definedSorts())) {
                sb.append("|");
                encodeStringToIdentifier(sb, s);
                sb.append("\n");
            }
            if (!mainModule.definedSorts().contains(Sorts.String())) {
                sb.append("|SortString\n");
            }
            if (!mainModule.definedSorts().contains(Sorts.Float())) {
                sb.append("|SortFloat\n");
            }
        }
        sb.append("type klabel = \n");
        if (fastCompilation) {
            sb.append("KLabel of string\n");
        } else {
            for (KLabel label : iterable(mainModule.definedKLabels())) {
                sb.append("|");
                encodeStringToIdentifier(sb, label);
                sb.append("\n");
            }
        }
        sb.append("let print_sort(c: sort) : string = match c with \n");
        for (Sort s : iterable(mainModule.definedSorts())) {
            sb.append("|");
            encodeStringToIdentifier(sb, s);
            sb.append(" -> ");
            sb.append(enquoteString(s.name()));
            sb.append("\n");
        }
        if (fastCompilation) {
            sb.append("| Sort s -> raise (Invalid_argument s)\n");
        }
        sb.append("let print_klabel(c: klabel) : string = match c with \n");
        for (KLabel label : iterable(mainModule.definedKLabels())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label);
            sb.append(" -> ");
            sb.append(enquoteString(ToKast.apply(label)));
            sb.append("\n");
        }
        if (fastCompilation) {
            sb.append("| KLabel s -> raise (Invalid_argument s)\n");
        }
        sb.append("let collection_for (c: klabel) : klabel = match c with \n");
        for (Map.Entry<KLabel, KLabel> entry : collectionFor.entrySet()) {
            sb.append("|");
            encodeStringToIdentifier(sb, entry.getKey());
            sb.append(" -> ");
            encodeStringToIdentifier(sb, entry.getValue());
            sb.append("\n");
        }
        sb.append("let unit_for (c: klabel) : klabel = match c with \n");
        for (KLabel label : collectionFor.values().stream().collect(Collectors.toSet())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label);
            sb.append(" -> ");
            encodeStringToIdentifier(sb, KLabel(mainModule.attributesFor().apply(label).<String>get(Attribute.UNIT_KEY).get()));
            sb.append("\n");
        }
        sb.append("let el_for (c: klabel) : klabel = match c with \n");
        for (KLabel label : collectionFor.values().stream().collect(Collectors.toSet())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label);
            sb.append(" -> ");
            encodeStringToIdentifier(sb, KLabel(mainModule.attributesFor().apply(label).<String>get("element").get()));
            sb.append("\n");
        }
        sb.append("let boolSort = ").append(BOOL);
        sb.append("\n and stringSort = ").append(STRING);
        sb.append("\n and intSort = ").append(INT);
        sb.append("\n and floatSort = ").append(FLOAT);
        sb.append("\n and setSort = ").append(SET);
        sb.append("\n and setConcatLabel = ").append(SET_CONCAT);
        sb.append("\n and listSort = ").append(LIST);
        sb.append("\n and listConcatLabel = ").append(LIST_CONCAT);
        return sb.toString();
    }

    public String definition() {
        StringBuilder sb = new StringBuilder();
        sb.append("open Prelude\nopen Constants\nopen Prelude.K\nopen Gmp\n");
        SetMultimap<KLabel, Rule> functionRules = HashMultimap.create();
        ListMultimap<KLabel, Rule> anywhereRules = ArrayListMultimap.create();
        anywhereKLabels = new HashSet<>();
        for (Rule r : iterable(mainModule.rules())) {
            K left = RewriteToTop.toLeft(r.body());
            if (left instanceof KSequence) {
                KSequence kseq = (KSequence) left;
                if (kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                    KApply kapp = (KApply) kseq.items().get(0);
                    if (mainModule.attributesFor().apply(kapp.klabel()).contains(Attribute.FUNCTION_KEY)) {
                        functionRules.put(kapp.klabel(), r);
                    }
                }
            }
        }
        functions = new HashSet<>(functionRules.keySet());
        for (Production p : iterable(mainModule.productions())) {
            if (p.att().contains(Attribute.FUNCTION_KEY)) {
                functions.add(p.klabel().get());
            }
        }

        String conn = "let rec ";
        for (KLabel functionLabel : functions) {
            sb.append(conn);
            String functionName = encodeStringToFunction(sb, functionLabel.name());
            sb.append(" (c: k list) (config: k) (guards: Guard.t) : k = let lbl = \n");
            encodeStringToIdentifier(sb, functionLabel);
            sb.append(" and sort = \n");
            encodeStringToIdentifier(sb, mainModule.sortFor().apply(functionLabel));
            String sortHook = "";
            if (mainModule.attributesFor().apply(functionLabel).contains(Attribute.PREDICATE_KEY)) {
                Sort sort = Sort(mainModule.attributesFor().apply(functionLabel).<String>get(Attribute.PREDICATE_KEY).get());
                sb.append(" and pred_sort = \n");
                encodeStringToIdentifier(sb, sort);
                if (mainModule.sortAttributesFor().contains(sort)) {
                    sortHook = mainModule.sortAttributesFor().apply(sort).<String>getOptional("hook").orElse("");
                }
            }
            sb.append(" in match c with \n");
            String hook = mainModule.attributesFor().apply(functionLabel).<String>getOptional(Attribute.HOOK_KEY).orElse(".");
            String namespace = hook.substring(0, hook.indexOf('.'));
            String function = hook.substring(namespace.length() + 1);
            if (hookNamespaces.contains(namespace)) {
                sb.append("| _ -> try ");
                sb.append(namespace);
                sb.append(".hook_");
                sb.append(function);
                sb.append(" c lbl sort config freshFunction\n");
                sb.append("with Not_implemented -> match c with \n");
            } else if (!hook.equals(".")) {
                kem.registerCompilerWarning("missing entry for hook " + hook);
            }
            if (predicateRules.containsKey(sortHook)) {
                sb.append("| ");
                sb.append(predicateRules.get(sortHook));
                sb.append("\n");
            }

            convertFunction( functionRules.get(functionLabel).stream().sorted(this::sortFunctionRules).collect(Collectors.toList()),
                    sb, functionName, RuleType.FUNCTION);
            sb.append("| _ -> raise (Stuck [KApply(lbl, c)])\n");
            conn = "and ";
        }
        for (KLabel functionLabel : anywhereKLabels) {
            sb.append(conn);
            String functionName = encodeStringToFunction(sb, functionLabel.name());
            sb.append(" (c: k list) (config: k) (guards: Guard.t) : k = let lbl = \n");
            encodeStringToIdentifier(sb, functionLabel);
            sb.append(" in match c with \n");
            convertFunction(anywhereRules.get(functionLabel), sb, functionName, RuleType.ANYWHERE);
            sb.append("| _ -> [KApply(lbl, c)]\n");
            conn = "and ";
        }

        sb.append("and freshFunction (sort: string) (config: k) (counter: Z.t) : k = match sort with \n");
        for (Sort sort : iterable(mainModule.freshFunctionFor().keys())) {
            sb.append("| \"");
            sb.append(sort.name());
            sb.append("\" -> (");
            KLabel freshFunction = mainModule.freshFunctionFor().apply(sort);
            sb.append(encodeStringToFunction(freshFunction.name()));
            sb.append(" ([Int counter] :: []) config Guard.empty)\n");
        }
        sb.append("and eval (c: kitem) (config: k) : k = match c with KApply(lbl, kl) -> (match lbl with \n");
        for (KLabel label : Sets.union(functions, anywhereKLabels)) {
            sb.append("|");
            encodeStringToIdentifier(sb, label);
            sb.append(" -> ");
            encodeStringToFunction(sb, label.name());
            sb.append(" kl config Guard.empty\n");
        }
        sb.append("| _ -> [c])\n");
        sb.append("| _ -> [c]\n");
        sb.append("let rec lookups_step (c: k) (config: k) (guards: Guard.t) : k = match c with \n");
        List<Rule> sortedRules = stream(mainModule.rules())
                .sorted((r1, r2) -> ComparisonChain.start()
                        .compareTrueFirst(r1.att().contains("structural"), r2.att().contains("structural"))
                        .compareFalseFirst(r1.att().contains("owise"), r2.att().contains("owise"))
                        .compareFalseFirst(indexesPoorly(r1), indexesPoorly(r2))
                        .result())
                .filter(r -> !functionRules.values().contains(r) && !r.att().contains(Attribute.MACRO_KEY) && !r.att().contains(Attribute.ANYWHERE_KEY))
                .collect(Collectors.toList());
        Map<Boolean, List<Rule>> groupedByLookup = sortedRules.stream()
                .collect(Collectors.groupingBy(this::hasLookups));
        convert(groupedByLookup.get(true), sb, "lookups_step", RuleType.REGULAR, 0);
        sb.append("| _ -> raise (Stuck c)\n");
        sb.append("let step (c: k) : k = let config = c in match c with \n");
        if (groupedByLookup.containsKey(false)) {
            for (Rule r : groupedByLookup.get(false)) {
                convert(r, sb, RuleType.REGULAR);
                if (fastCompilation) {
                    sb.append("| _ -> match c with \n");
                }
            }
        }
        sb.append("| _ -> lookups_step c c Guard.empty\n");
        sb.append(postlude);
        return sb.toString();
    }

    private StringBuilder convertFunction(List<Rule> rules, String functionName, RuleType type) {
        StringBuilder sb = new StringBuilder();
        int ruleNum = 0;
        for (Rule r : rules) {
            if (hasLookups(r)) {
                ruleNum = convert(Collections.singletonList(r),
                                  sb,
                                  functionName,
                                  type,
                                  ruleNum);
            } else {
                sb.append(convert(r, type));
            }
            if (fastCompilation) {
                sb.append("| _ -> match c with \n");
            }
        }

        return sb;
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

    private int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"), a2.att().contains("owise"));
    }

    private List<List<KLabel>> sortFunctions(SetMultimap<KLabel, Rule> functionRules) {
        BiMap<KLabel, Integer> mapping = HashBiMap.create();
        int counter = 0;
        for (KLabel lbl : functions) {
            mapping.put(lbl, counter++);
        }
        List<Integer>[] predecessors = new List[functions.size()];
        for (int i = 0; i < predecessors.length; i++) {
            predecessors[i] = new ArrayList<>();
        }

        class GetPredecessors extends VisitKORE {
            private final KLabel current;

            public GetPredecessors(KLabel current) {
                this.current = current;
            }

            @Override
            public Void apply(KApply k) {
                if (functions.contains(k.klabel())) {
                    predecessors[mapping.get(current)].add(mapping.get(k.klabel()));
                }
                return super.apply(k);
            }
        }

        for (Map.Entry<KLabel, Rule> entry : functionRules.entries()) {
            GetPredecessors visitor = new GetPredecessors(entry.getKey());
            visitor.apply(entry.getValue().body());
            visitor.apply(entry.getValue().requires());
        }

        List<List<Integer>> components = new SCCTarjan().scc(predecessors);

        return components.stream().map(l -> l.stream()
                .map(i -> mapping.inverse().get(i)).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    private static enum RuleType {
        FUNCTION, ANYWHERE, REGULAR, PATTERN
    }

    private static class VarInfo {
        final SetMultimap<KVariable, String> vars;
        final Map<String, KLabel> listVars;

        VarInfo() { this(HashMultimap.create(), new HashMap<>()); }

        VarInfo(VarInfo vars) {
            this(HashMultimap.create(vars.vars), new HashMap<>(vars.listVars));
        }

        VarInfo(SetMultimap<KVariable, String> vars, Map<String, KLabel> listVars) {
            this.vars = vars;
            this.listVars = listVars;
        }
    }

    private Tuple2<Integer, StringBuilder convert(List<Rule> rules,
                                                  String functionName,
                                                  RuleType ruleType,
                                                  int ruleNum) {
        StringBuilder sb = new StringBuilder();
        NormalizeVariables t = new NormalizeVariables();
        Map<AttCompare, List<Rule>> grouping = rules.stream().collect(
                Collectors.groupingBy(r -> new AttCompare(t.normalize(RewriteToTop.toLeft(r.body())), "sort")));
        Map<Tuple3<AttCompare, KLabel, AttCompare>, List<Rule>> groupByFirstPrefix = new HashMap<>();
        for (Map.Entry<AttCompare, List<Rule>> entry : grouping.entrySet()) {
            AttCompare left = entry.getKey();
            groupByFirstPrefix.putAll(entry.getValue().stream()
                    .collect(Collectors.groupingBy(r -> {
                        KApply lookup = getLookup(r, 0);
                        if (lookup == null) return null;
                        //reconstruct the denormalization for this particular rule
                        K left2 = t.normalize(RewriteToTop.toLeft(r.body()));
                        K normal = t.normalize(t.applyNormalization(lookup.klist().items().get(1), left2));
                        return Tuple3.apply(left, lookup.klabel(), new AttCompare(normal, "sort"));
                    })));
        }
        List<Rule> owiseRules = new ArrayList<>();
        for (Map.Entry<Tuple3<AttCompare, KLabel, AttCompare>, List<Rule>> entry2 : groupByFirstPrefix.entrySet().stream().sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size())).collect(Collectors.toList())) {
            K left = entry2.getKey()._1().get();
            VarInfo globalVars = new VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ruleType, left, globalVars));
            K lookup;
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.append(ruleNum);
            sb.append(") guards)");
            if (entry2.getValue().size() == 1) {
                Rule r = entry2.getValue().get(0);
                sb.append(convertComment(r));

                left = t.normalize(RewriteToTop.toLeft(r.body()));
                lookup = t.normalize(t.applyNormalization(getLookup(r, 0).klist()
                                                                         .items()
                                                                         .get(1),
                                                          left));
                r = t.normalize(t.applyNormalization(r, left, lookup));

                List<Lookup> lookups = convertLookups(r.requires(),
                                                      globalVars,
                                                      functionName,
                                                      ruleNum,
                                                      false);

                SBPair pair = convertSideCondition(r.requires(),
                                                   globalVars,
                                                   lookups,
                                                   lookups.size() > 0);
                sb.append(pair.prefix);
                sb.append(" -> ");
                sb.append(convertRHS(ruleType,
                                     RewriteToTop.toRight(r.body()),
                                     globalVars,
                                     pair.suffix));
                ruleNum++;
            } else {
                KToken dummy = KToken("dummy", Sort("Dummy"));
                Object key2  = entry2.getKey();
                KApply kapp  = KApply(key2._2(),
                                      dummy,
                                      key2._3().get());
                List<Lookup> lookupList  = convertLookups(kapp,
                                                          globalVars,
                                                          functionName,
                                                          ruleNum++,
                                                          true);
                sb.append(lookupList.get(0).prefix);
                for (Rule r : entry2.getValue()) {
                    if (indexesPoorly(r) || r.att().contains("owise")) {
                        owiseRules.add(r);
                    } else {
                        sb.append(convertComment(r));

                        //reconstruct the denormalization for this particular rule
                        left = t.normalize(RewriteToTop.toLeft(r.body()));
                        lookup = t.normalize(t.applyNormalization(getLookup(r, 0).klist()
                                                                                 .items()
                                                                                 .get(1),
                                                                  left));
                        r = t.normalize(t.applyNormalization(r, left, lookup));

                        VarInfo vars = new VarInfo(globalVars);
                        List<Lookup> lookups = convertLookups(r.requires(),
                                                              vars,
                                                              functionName,
                                                              ruleNum,
                                                              true);
                        sb.append(lookups.get(0).pattern);
                        lookups.remove(0);
                        sb.append(" when not (Guard.mem (GuardElt.Guard ");
                        sb.append(ruleNum);
                        sb.append(") guards)");
                        SBPair pair = convertSideCondition(r.requires(),
                                                           vars,
                                                           lookups,
                                                           lookups.size() > 0);
                        sb.append(pair.prefix);
                        sb.append(" -> ");
                        sb.append(convertRHS(ruleType,
                                             RewriteToTop.toRight(r.body()),
                                             vars,
                                             pair.suffix));
                        ruleNum++;
                        if (fastCompilation) {
                            sb.append("| _ -> match e with \n");
                        }
                    }
                }
                sb.append(lookupList.get(0).suffix);
                sb.append("\n");
            }
        }
        for (Rule r : owiseRules) {
            VarInfo globalVars = new VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ruleType,
                                 RewriteToTop.toLeft(r.body()),
                                 globalVars));
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.append(ruleNum);
            sb.append(") guards)");

            sb.append(convertComment(r));
            List<Lookup> lookups = convertLookups(r.requires(),
                                                  globalVars,
                                                  functionName,
                                                  ruleNum,
                                                  false);
            SBPair pair = convertSideCondition(r.requires(),
                                               globalVars,
                                               lookups,
                                               lookups.size() > 0);
            sb.append(" -> ");
            sb.append(convertRHS(ruleType,
                                 RewriteToTop.toRight(r.body()),
                                 globalVars,
                                 suffix));
            ruleNum++;
        }
        return ruleNum;
    }

    private boolean indexesPoorly(Rule r) {
        class Holder { boolean b; }
        Holder h = new Holder();
        VisitKORE visitor = new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if (k.klabel().name().equals("<k>")) {
                    if (k.klist().items().size() == 1) {
                        if (k.klist().items().get(0) instanceof KSequence) {
                            KSequence kCell = (KSequence) k.klist().items().get(0);
                            if (kCell.items().size() == 2 && kCell.items().get(1) instanceof KVariable) {
                                if (kCell.items().get(0) instanceof KVariable) {
                                    Sort s = Sort(kCell.items().get(0).att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
                                    if (mainModule.sortAttributesFor().contains(s)) {
                                        String hook = mainModule.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
                                        if (!sortVarHooks.containsKey(hook)) {
                                            h.b = true;
                                        }
                                    } else {
                                        h.b = true;
                                    }
                                } else if (kCell.items().get(0) instanceof KApply) {
                                    KApply kapp = (KApply) kCell.items().get(0);
                                    if (kapp.klabel() instanceof KVariable) {
                                        h.b = true;
                                    }
                                }
                            }
                        }
                    }
                }
                return super.apply(k);
            }
        };
        visitor.apply(RewriteToTop.toLeft(r.body()));
        visitor.apply(r.requires());
        return h.b;
    }

    private KApply getLookup(Rule r, int idx) {
        class Holder {
            int i = 0;
            KApply lookup;
        }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if (h.i > idx)
                    return null;
                if (k.klabel().name().equals("#match")
                        || k.klabel().name().equals("#setChoice")
                        || k.klabel().name().equals("#mapChoice")) {
                    h.lookup = k;
                    h.i++;
                }
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.lookup;
    }

    private void convert(Rule r, StringBuilder sb, RuleType type) {
        try {
            convertComment(r, sb);
            sb.append("| ");
            K left = RewriteToTop.toLeft(r.body());
            K right = RewriteToTop.toRight(r.body());
            K requires = r.requires();
            VarInfo vars = new VarInfo();
            convertLHS(sb, type, left, vars);
            String result = convert(vars);
            String suffix = "";
            if (!requires.equals(KSequence(BooleanUtils.TRUE)) || !result.equals("true")) {
                suffix = convertSideCondition(sb, requires, vars, Collections.emptyList(), true);
            }
            sb.append(" -> ");
            convertRHS(sb, type, right, vars, suffix);
        } catch (KEMException e) {
            e.exception.addTraceFrame("while compiling rule at " + r.att().getOptional(Source.class).map(Object::toString).orElse("<none>") + ":" + r.att().getOptional(Location.class).map(Object::toString).orElse("<none>"));
            throw e;
        }
    }

    private void convertLHS(StringBuilder sb, RuleType type, K left, VarInfo vars) {
        Visitor visitor = convert(sb, false, vars, false, false);
        if (type == RuleType.ANYWHERE || type == RuleType.FUNCTION) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            visitor.apply(kapp.klist().items(), true);
        } else {
            visitor.apply(left);
        }
    }

    private void convertComment(Rule r, StringBuilder sb) {
        sb.append("(* rule ");
        sb.append(ToKast.apply(r.body()));
        sb.append(" requires ");
        sb.append(ToKast.apply(r.requires()));
        sb.append(" ensures ");
        sb.append(ToKast.apply(r.ensures()));
        sb.append(" ");
        sb.append(r.att().toString());
        sb.append("*)\n");
    }

    private void convertRHS(StringBuilder sb, RuleType type, K right, VarInfo vars, String suffix) {
        if (type == RuleType.ANYWHERE) {
            sb.append("(match ");
        }
        if (type == RuleType.PATTERN) {
            for (KVariable var : vars.vars.keySet()) {
                sb.append("(Subst.add \"");
                sb.append(var.name());
                sb.append("\" ");
                boolean isList = isList(var, false, true, vars, false);
                if (!isList) {
                    sb.append("[");
                }
                sb.append(vars.vars.get(var).iterator().next());
                if (!isList) {
                    sb.append("]");
                }
                sb.append(" ");
            }
            sb.append("Subst.empty");
            for (KVariable var : vars.vars.keySet()) {
                sb.append(")");
            }
        } else {
            convert(sb, true, vars, false, type == RuleType.ANYWHERE).apply(right);
        }
        if (type == RuleType.ANYWHERE) {
            sb.append(" with [item] -> eval item config)");
        }
        sb.append(suffix);
        sb.append("\n");
    }

    private String convertSideCondition(StringBuilder sb, K requires, VarInfo vars, List<Lookup> lookups, boolean when) {
        String result;
        for (Lookup lookup : lookups) {
            sb.append(lookup.prefix);
            sb.append(lookup.pattern);
        }
        result = convert(vars);
        sb.append(when ? " when " : " && ");
        convert(sb, true, vars, true, false).apply(requires);
        sb.append(" && (");
        sb.append(result);
        sb.append(")");
        return Lists.reverse(lookups).stream().map(l -> l.suffix).reduce("", String::concat);
    }

    private static class Holder { String reapply; boolean first; }

    private static class Lookup {
        final String prefix;
        final String pattern;
        final String suffix;

        public Lookup(String prefix, String pattern, String suffix) {
            this.prefix = prefix;
            this.pattern = pattern;
            this.suffix = suffix;
        }
    }

    private List<Lookup> convertLookups(K requires, VarInfo vars, String functionName, int ruleNum, boolean hasMultiple) {
        List<Lookup> results = new ArrayList<>();
        Holder h = new Holder();
        h.first = hasMultiple;
        h.reapply = "(" + functionName + " c config (Guard.add (GuardElt.Guard " + ruleNum + ") guards))";
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if (k.klabel().name().equals("#match")) {
                    if (k.klist().items().size() != 2) {
                        throw KEMException.internalError("Unexpected arity of lookup: " + k.klist().size(), k);
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(" -> (let e = ");
                    convert(sb, true, vars, false, false).apply(k.klist().items().get(1));
                    sb.append(" in match e with \n");
                    sb.append("| [Bottom] -> ");
                    sb.append(h.reapply);
                    sb.append("\n");
                    String prefix = sb.toString();
                    sb = new StringBuilder();
                    sb.append("| ");
                    convert(sb, false, vars, false, false).apply(k.klist().items().get(0));
                    String pattern = sb.toString();
                    String suffix = "| _ -> " + h.reapply + ")";
                    results.add(new Lookup(prefix, pattern, suffix));
                    h.first = false;
                } else if (k.klabel().name().equals("#setChoice")) {
                    choose(k, "| [Set (_,_,collection)] -> let choice = (KSet.fold (fun e result -> ");
                } else if (k.klabel().name().equals("#mapChoice")) {
                    choose(k, "| [Map (_,_,collection)] -> let choice = (KMap.fold (fun e v result -> ");
                }
                return super.apply(k);
            }

            private void choose(KApply k, String choiceString) {
                if (k.klist().items().size() != 2) {
                    throw KEMException.internalError("Unexpected arity of choice: " + k.klist().size(), k);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(" -> (match ");
                convert(sb, true, vars, false, false).apply(k.klist().items().get(1));
                sb.append(" with \n");
                sb.append(choiceString);
                if (h.first) {
                    sb.append("let rec stepElt = fun guards -> ");
                }
                sb.append("if (compare result [Bottom]) = 0 then (match e with ");
                String prefix = sb.toString();
                sb = new StringBuilder();
                String suffix2 = "| _ -> [Bottom]) else result" + (h.first ? " in stepElt Guard.empty" : "") + ") collection [Bottom]) in if (compare choice [Bottom]) = 0 then " + h.reapply + " else choice";
                String suffix = suffix2 + "| _ -> " + h.reapply + ")";
                if (h.first) {
                    h.reapply = "(stepElt (Guard.add (GuardElt.Guard " + ruleNum + ") guards))";
                } else {
                    h.reapply = "[Bottom]";
                }
                sb.append("| ");
                convert(sb, false, vars, false, false).apply(k.klist().items().get(0));
                String pattern = sb.toString();
                results.add(new Lookup(prefix, pattern, suffix));
                h.first = false;
            }
        }.apply(requires);
        return results;
    }

    private String convert(VarInfo vars) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<KVariable, Collection<String>> entry : vars.vars.asMap().entrySet()) {
            Collection<String> nonLinearVars = entry.getValue();
            if (nonLinearVars.size() < 2) {
                continue;
            }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                if (!isList(entry.getKey(), false, true, vars, false)) {
                    sb.append("((compare_kitem ");
                } else{
                    sb.append("((compare ");
                }
                applyVarRhs(last, sb, vars.listVars.get(last));
                sb.append(" ");
                applyVarRhs(next, sb, vars.listVars.get(next));
                sb.append(") = 0)");
                last = next;
                sb.append(" && ");
            }
        }
        sb.append("true");
        return sb.toString();
    }

    private void applyVarRhs(KVariable v, StringBuilder sb, VarInfo vars) {
        applyVarRhs(vars.vars.get(v).iterator().next(), sb, vars.listVars.get(vars.vars.get(v).iterator().next()));
    }

    private void applyVarRhs(String varOccurrance, StringBuilder sb, KLabel listVar) {
        if (listVar != null) {
            sb.append("(List (");
            encodeStringToIdentifier(sb, mainModule.sortFor().apply(listVar));
            sb.append(", ");
            encodeStringToIdentifier(sb, listVar);
            sb.append(", ");
            sb.append(varOccurrance);
            sb.append("))");
        } else {
            sb.append(varOccurrance);
        }
    }

    private void applyVarLhs(KVariable k, StringBuilder sb, VarInfo vars) {
        String varName = encodeStringToVariable(k.name());
        vars.vars.put(k, varName);
        Sort s = Sort(k.att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
        if (mainModule.sortAttributesFor().contains(s)) {
            String hook = mainModule.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
            if (sortVarHooks.containsKey(hook)) {
                sb.append("(");
                sb.append(sortVarHooks.get(hook).apply(s));
                sb.append(" as ");
                sb.append(varName);
                sb.append(")");
                return;
            }
        }
        sb.append(varName);
    }

    private Visitor convert(StringBuilder sb, boolean rhs, VarInfo vars, boolean useNativeBooleanExp, boolean anywhereRule) {
        return new Visitor(sb, rhs, vars, useNativeBooleanExp, anywhereRule);
    }

    public static String getSortOfVar(KVariable k, VarInfo vars) {
        if (vars.vars.containsKey(k)) {
            String varName = vars.vars.get(k).iterator().next();
            if (vars.listVars.containsKey(varName)) {
                return vars.listVars.get(varName).name();
            }
        }
        return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
    }

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match") || k.klabel().name().equals("#mapChoice") || k.klabel().name().equals("#setChoice");
    }

    private boolean isList(K item, boolean klist, boolean rhs, VarInfo vars, boolean anywhereRule) {
        return !klist && ((item instanceof KVariable && getSortOfVar((KVariable)item, vars).equals("K")) || item instanceof KSequence
                || (item instanceof KApply && (functions.contains(((KApply) item).klabel()) || (((anywhereKLabels.contains(((KApply) item).klabel()) && !anywhereRule) || ((KApply) item).klabel() instanceof KVariable) && rhs))))
                && !(!rhs && item instanceof KApply && collectionFor.containsKey(((KApply) item).klabel()));
    }

}


// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------

    /**
     * Flag that determines whether or not we annotate output OCaml with rules
     */
    public static final boolean annotateOutput = false;

    /**
     * Flag that determines whether or not we output debug info
     */
    public static final boolean debugEnabled = true;


    private final KExceptionManager kem;
    private final SyntaxBuilder ocamlDef;

    private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
    private static final SyntaxBuilder wildcardSB, bottomSB, choiceSB, resultSB;

    static {
        wildcardSB = newsbv("_");
        bottomSB = newsbv("[Bottom]");
        choiceSB = newsbv("choice");
        resultSB = newsbv("result");
        setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
                                 "e", "result");
        mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
                                 "k", "v", "result");
    }

    /**
     * Constructor for DefinitionToFunc
     */
    public DefinitionToFunc(KExceptionManager kem,
                            PreprocessedKORE preproc) {
        this.kem = kem;
        this.ocamlDef = langDefToFunc(preproc);
        debugOutput(preproc);
    }

    public String genOCaml() {
        return ocamlDef.toString();
    }

    private void debugOutput(PreprocessedKORE ppk) {
        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");

        outprintfln(";; %s", ocamlDef.trackPrint()
                                     .replaceAll("\n", "\n;; "));
        outprintfln(";; Number of parens: %d", ocamlDef.getNumParens());
        outprintfln(";; Number of lines:  %d", ocamlDef.getNumLines());


        outprintfln("functionSet: %s", ppk.functionSet);
        outprintfln("anywhereSet: %s", ppk.anywhereSet);

        XMLBuilder outXML = ocamlDef.getXML();


        outprintfln("");

        try {
            outprintfln("%s", outXML.renderSExpr());
        } catch(KEMException e) {
            outprintfln(";; %s", outXML.toString()
                        .replaceAll("><", ">\n<")
                        .replaceAll("\n", "\n;; "));
        }


        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");
    }

    private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        sb.append(FuncConstants.genConstants(ppk));
        sb.append(addFunctions(ppk));
        sb.append(addSteps(ppk));
        sb.append(OCamlIncludes.postludeSB);

        return sb;
    }

    private SyntaxBuilder addFunctionMatch(String functionName,
                                           KLabel functionLabel,
                                           PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        String hook = ppk.attrLabels
                         .get(Attribute.HOOK_KEY)
                         .getOrDefault(functionLabel, "");
        String fn = functionLabel.name();
        boolean isHook = OCamlIncludes.hooks.containsKey(hook);
        boolean isPred = OCamlIncludes.predicateRules.containsKey(fn);
        Collection<Rule> rules = ppk.functionRulesOrdered
                                    .getOrDefault(functionLabel, newArrayList());

        if(!isHook && !hook.isEmpty()) {
            kem.registerCompilerWarning("missing entry for hook " + hook);
        }

        sb.beginMatchExpression(newsbv("c"));

        if(isHook) {
            sb.append(OCamlIncludes.hooks.get(hook));
        }

        if(isPred) {
            sb.append(OCamlIncludes.predicateRules.get(fn));
        }

        int i = 0;
        for(Rule r : rules) {
            sb.append(oldConvert(ppk, r, true, i++, functionName));
        }

        sb.addMatchEquation(wildcardSB, raiseStuck(newsbv("[KApply(lbl, c)]")));
        sb.endMatchExpression();

        return sb;
    }

    private SyntaxBuilder addFunctionEquation(KLabel functionLabel,
                                              PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        String functionName = encodeStringToFunction(functionLabel.name());

        sb.beginLetrecEquation();
        sb.addLetrecEquationName(newsb()
                                 .beginRender()
                                 .addValue(functionName)
                                 .addSpace()
                                 .addValue("(c: k list)")
                                 .addSpace()
                                 .addValue("(guards: Guard.t)")
                                 .addSpace()
                                 .addKeyword(":")
                                 .addSpace()
                                 .addValue("k")
                                 .endRender());
        sb.beginLetrecEquationValue();
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.addLetEquation(newsb("lbl"),
                          newsb(encodeStringToIdentifier(functionLabel)));
        sb.endLetDefinitions();

        sb.beginLetScope();
        sb.append(addFunctionMatch(functionName, functionLabel, ppk));
        sb.endLetScope();

        sb.endLetExpression();
        sb.endLetrecEquationValue();
        sb.endLetrecEquation();

        return sb;
    }

    private SyntaxBuilder addFreshFunction(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        sb.beginLetrecEquation();
        sb.addLetrecEquationName(newsb()
                                 .beginRender()
                                 .addValue("freshFunction")
                                 .addSpace()
                                 .addValue("(sort: string)")
                                 .addSpace()
                                 .addValue("(counter: Z.t)")
                                 .addSpace()
                                 .addKeyword(":")
                                 .addSpace()
                                 .addValue("k")
                                 .endRender());
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

        return sb;
    }

    private SyntaxBuilder addEval(Set<KLabel> labels,
                                  PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

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
        sb.endMatchEquation();

        sb.addMatchEquation(wildcardSB, newsbv("[c]"));

        sb.endMatchExpression();

        sb.endLetrecEquationValue();
        sb.endLetrecEquation();

        return sb;
    }

    private SyntaxBuilder addFunctions(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        Set<KLabel> functions = ppk.functionSet;
        Set<KLabel> anywheres = ppk.anywhereSet;

        Set<KLabel> funcAndAny = Sets.union(functions, anywheres);


        for(List<KLabel> component : ppk.functionOrder) {
            sb.beginLetrecDeclaration();
            sb.beginLetrecDefinitions();
            for(KLabel functionLabel : component) {
                sb.append(addFunctionEquation(functionLabel, ppk));
            }
            sb.endLetrecDefinitions();
            sb.endLetrecDeclaration();
        }

        sb.beginLetrecDeclaration();
        sb.beginLetrecDefinitions();

        sb.append(addFreshFunction(ppk));

        sb.append(addEval(funcAndAny, ppk));

        sb.endLetrecDefinitions();
        sb.endLetrecDeclaration();

        return sb;
    }

    private SyntaxBuilder makeStuck(SyntaxBuilder body) {
        return newsb().addApplication("Stuck", body);
    }

    private SyntaxBuilder raiseStuck(SyntaxBuilder body) {
        return newsb().addApplication("raise", makeStuck(body));
    }

    private SyntaxBuilder addSteps(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

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
                sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "lookups_step")));
            }
        }

        sb.addMatchEquation(wildcardSB, raiseStuck(newsb("c")));
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
                sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "step")));
            }
        }
        sb.addMatchEquation(newsb("_"),
                            newsb("lookups_step c Guard.empty"));
        sb.endMatchExpression();
        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetDeclaration();

        return sb;
    }

    private SyntaxBuilder debugMismatch(SyntaxBuilder sb) {
        Pattern xmlBegPat = Pattern.compile("<[^<>/]*>");
        Pattern xmlEndPat = Pattern.compile("</[^<>/]*>");
        Map<String, Integer> sbMap = sb.getTrack();
        List<String> sbKeys = sbMap.keySet()
                                   .stream()
                                   .filter(x -> xmlEndPat.matcher(x).matches())
                                   .map(x -> x.substring(2, x.length() - 1))
                                   .collect(toList());
        Map<String, Integer> mismatched = newHashMap();
        for(String k : sbKeys) {
            int begin = sbMap.get("<"  + k + ">").intValue();
            int end   = sbMap.get("</" + k + ">").intValue();
            if(begin != end) { mismatched.put(k, begin - end); }
        }
        if(! mismatched.isEmpty()) {
            outprintfln(";; ---------------- ERROR ----------------");
            outprintfln(";; The following were mismatched:");
            for(String k : mismatched.keySet()) {
                outprintfln(";; %30s --> %s", k, mismatched.get(k));
            }
            outprintfln(";; ----------------");
            outprintfln(";; XML:\n;; %s",
                        sb.pretty().stream().collect(joining("\n;; ")));
            outprintfln(";; ---------------- ERROR ----------------");
        }
        return sb;
    }

    private SyntaxBuilder outputAnnotate(Rule r) {
        SyntaxBuilder sb = newsb();

        sb.beginMultilineComment();
        sb.appendf("rule %s requires %s ensures %s %s",
                   ToKast.apply(r.body()),
                   ToKast.apply(r.requires()),
                   ToKast.apply(r.ensures()),
                   r.att().toString());
        sb.endMultilineComment();
        sb.addNewline();

        return sb;
    }

    private SyntaxBuilder unhandledOldConvert(PreprocessedKORE ppk,
                                              Rule r,
                                              boolean isFunction,
                                              int ruleNum,
                                              String functionName) throws KEMException {
        SyntaxBuilder sb = newsb();

        if(annotateOutput) { sb.append(outputAnnotate(r)); }

        K left     = RewriteToTop.toLeft(r.body());
        K right    = RewriteToTop.toRight(r.body());
        K requires = r.requires();

        Set<String> indices = ppk.indexedRules.get(r);
        SetMultimap<KVariable, String> vars = HashMultimap.create();
        FuncVisitor visitor = oldConvert(ppk, false, vars, false);

        sb.beginMatchEquation();
        sb.beginMatchEquationPattern();
        sb.append(handleLeft(isFunction, left, visitor));

        sb.append(handleLookup(indices, ruleNum));

        SBPair side = handleSideCondition(ppk, vars, functionName, ruleNum, requires);

        sb.append(side.getFst());
        sb.endMatchEquationPattern();
        sb.beginMatchEquationValue();
        sb.append(oldConvert(ppk, true, vars, false).apply(right));

        sb.endMatchEquationValue();
        sb.endMatchEquation();
        sb.append(side.getSnd());

        return sb;
    }

    private SyntaxBuilder handleLeft(boolean isFunction,
                                     K left,
                                     FuncVisitor visitor) {
        if(isFunction) {
            return handleFunction(left, visitor);
        } else {
            return handleNonFunction(left, visitor);
        }
    }

    private SyntaxBuilder handleFunction(K left, FuncVisitor visitor) {
        KApply kapp = (KApply) ((KSequence) left).items().get(0);
        return visitor.apply(kapp.klist().items(), true);
    }

    private SyntaxBuilder handleNonFunction(K left, FuncVisitor visitor) {
        return visitor.apply(left);
    }

    private SyntaxBuilder handleLookup(Set<String> indices, int ruleNum) {
        if(indices.contains("lookup")) {
            return newsb()
                .beginRender()
                .addSpace()
                .addKeyword("when")
                .addSpace()
                .addKeyword("not")
                .addSpace()
                .beginApplication()
                .addFunction("Guard.mem")
                .beginArgument()
                .addApplication("GuardElt.Guard", newsbf("%d", ruleNum))
                .endArgument()
                .addArgument(newsb("guards"))
                .endApplication()
                .endRender();
        } else {
            return newsb();
        }
    }

    private SBPair handleSideCondition(PreprocessedKORE ppk,
                                       SetMultimap<KVariable, String> vars,
                                       String functionName,
                                       int ruleNum,
                                       K requires) {
         SBPair convLookups = oldConvertLookups(ppk, requires, vars,
                                               functionName, ruleNum);

         SyntaxBuilder result = oldConvert(vars);

         if(hasSideCondition(requires, result.toString())) {
             SyntaxBuilder fstSB =
                 convLookups
                 .getFst()
                 .beginRender()
                 .addSpace()
                 .addKeyword("when")
                 .addSpace()
                 .beginRender()
                 .append(oldConvert(ppk, true, vars, true).apply(requires))
                 .endRender()
                 .addSpace()
                 .addKeyword("&&")
                 .addSpace()
                 .beginParenthesis()
                 .beginRender()
                 .append(result)
                 .endRender()
                 .endParenthesis()
                 .endRender();
             SyntaxBuilder sndSB = convLookups.getSnd();
             return newSBPair(fstSB, sndSB);
         } else {
             return newSBPair(newsb(), newsb());
         }
    }

    private boolean hasSideCondition(K requires, String result) {
        return !(KSequence(BooleanUtils.TRUE).equals(requires))
            || !("true".equals(result));
    }

    private SyntaxBuilder oldConvert(PreprocessedKORE ppk,
                                     Rule r,
                                     boolean function,
                                     int ruleNum,
                                     String functionName) {
        try {
            return unhandledOldConvert(ppk, r, function, ruleNum, functionName);
        } catch (KEMException e) {
            String src = r.att()
                          .getOptional(Source.class)
                          .map(Object::toString)
                          .orElse("<none>");
            String loc = r.att()
                          .getOptional(Location.class)
                          .map(Object::toString)
                          .orElse("<none>");
            e.exception
             .addTraceFrame(String.format("while compiling rule at %s: %s",
                                          src, loc));
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

    private static final SyntaxBuilder choiceSB1(String chChoiceVar,
                                                 String chFold,
                                                 String chPat,
                                                 String... chArgs) {
        return newsb()
            .beginMatchEquation()
            .addMatchEquationPattern(newsbv(chPat))
            .beginMatchEquationValue()
            .beginLetExpression()
            .beginLetDefinitions()
            .beginLetEquation()
            .addLetEquationName(choiceSB)
            .beginLetEquationValue()
            .beginApplication()
            .addFunction(chFold)
            .beginArgument()
            .beginLambda(chArgs)
            .beginConditional()
            .addConditionalIf()
            .append(newsb().addApplication("eq", resultSB, bottomSB))
            .addConditionalThen()
            .beginMatchExpression(newsbv(chChoiceVar));
    }


    private static final SyntaxBuilder choiceSB2(String chVar,
                                                 int ruleNum,
                                                 String functionName) {
        String guardCon = "GuardElt.Guard";
        String guardAdd = "Guard.add";
        SyntaxBuilder rnsb = newsbv(Integer.toString(ruleNum));

        SyntaxBuilder guardSB =
            newsb().addApplication(guardAdd,
                                   newsb().addApplication(guardCon, rnsb),
                                   newsbv("guards"));
        SyntaxBuilder condSB =
            newsb().addConditional(newsb().addApplication("eq", choiceSB, bottomSB),
                                   newsb().addApplication(functionName,
                                                          newsbv("c"),
                                                          guardSB),
                                   choiceSB);

        return newsb()
            .addMatchEquation(wildcardSB, bottomSB)
            .endMatchExpression()
            .addConditionalElse()
            .append(resultSB)
            .endConditional()
            .endLambda()
            .endArgument()
            .addArgument(newsbv(chVar))
            .addArgument(bottomSB)
            .endApplication()
            .endLetEquationValue()
            .endLetEquation()
            .endLetDefinitions()
            .addLetScope(condSB)
            .endLetExpression()
            .endMatchEquationValue()
            .endMatchEquation();
    }

    // TODO(remy): this needs refactoring very badly
    private SBPair oldConvertLookups(PreprocessedKORE ppk,
                                     K requires,
                                     SetMultimap<KVariable, String> vars,
                                     String functionName,
                                     int ruleNum) {
        Deque<SyntaxBuilder> suffStack = new ArrayDeque<>();

        SyntaxBuilder res = new SyntaxBuilder();
        SyntaxBuilder setChoiceSB2 = choiceSB2("s", ruleNum, functionName);
        SyntaxBuilder mapChoiceSB2 = choiceSB2("m", ruleNum, functionName);
        String formatSB3 = "(%s c (Guard.add (GuardElt.Guard %d) guards))";
        SyntaxBuilder sb3 =
            newsb().addMatchEquation(wildcardSB, newsbf(formatSB3,
                                                        functionName,
                                                        ruleNum))
                   .endMatchExpression()
                   .endMatchEquationValue()
                   .endMatchEquation();

        new VisitKORE() {
            private SyntaxBuilder sb1;
            private SyntaxBuilder sb2;
            private String functionStr;
            private int arity;

            @Override
            public Void apply(KApply k) {
                List<K> kitems = k.klist().items();
                String klabel = k.klabel().name();

                boolean choiceOrMatch = true;

                switch(klabel) {
                case "#match":
                    functionStr = "lookup";
                    sb1 = newsb();
                    sb2 = newsb();
                    arity = 2;
                    break;
                case "#setChoice":
                    functionStr = "set choice";
                    sb1 = setChoiceSB1;
                    sb2 = setChoiceSB2;
                    arity = 2;
                    break;
                case "#mapChoice":
                    functionStr = "map choice";
                    sb1 = mapChoiceSB1;
                    sb2 = mapChoiceSB2;
                    arity = 2;
                    break;
                default:
                    choiceOrMatch = false;
                    break;
                }

                if(choiceOrMatch) {
                    // prettyStackTrace();

                    checkApplyArity(k, arity, functionStr);

                    K kLabel1 = kitems.get(0);
                    K kLabel2 = kitems.get(1);

                    SyntaxBuilder luMatchValue
                        = oldConvert(ppk, true,  vars, false).apply(kLabel2);
                    SyntaxBuilder luLevelUp     = sb1;
                    SyntaxBuilder luPattern
                        = oldConvert(ppk, false, vars, false).apply(kLabel1);
                    SyntaxBuilder luWildcardEqn = sb3;
                    SyntaxBuilder luLevelDown   = sb2;

                    res.endMatchEquationPattern();
                    res.beginMatchEquationValue();
                    res.beginMatchExpression(luMatchValue);
                    res.append(luLevelUp);
                    res.beginMatchEquation();
                    res.beginMatchEquationPattern();
                    res.append(luPattern);

                    suffStack.add(luWildcardEqn);
                    suffStack.add(luLevelDown);
                }

                return super.apply(k);
            }
        }.apply(requires);

        SyntaxBuilder suffSB = new SyntaxBuilder();
        while(!suffStack.isEmpty()) { suffSB.append(suffStack.pollLast()); }

        return newSBPair(res, suffSB);
    }

    private static String prettyStackTrace() {
        Pattern funcPat = Pattern.compile("^org[.]kframework.*$");

        SyntaxBuilder sb = newsb();
        sb.append(";; DBG: ----------------------------\n");
        sb.append(";; DBG: apply executed:\n");
        StackTraceElement[] traceArray = Thread.currentThread().getStackTrace();
        List<StackTraceElement> trace = newArrayList(traceArray.length);

        for(StackTraceElement ste : traceArray) { trace.add(ste); }

        trace = trace
            .stream()
            .filter(x -> funcPat.matcher(x.toString()).matches())
            .collect(toList());

        int skip = 1;
        for(StackTraceElement ste : trace) {
            if(skip > 0) {
                skip--;
            } else {
                sb.appendf(";; DBG: trace: %20s %6s %30s\n",
                           ste.getMethodName(),
                           "(" + Integer.toString(ste.getLineNumber()) + ")",
                           "(" + ste.getFileName() + ")");
            }
        }
        return sb.toString();
    }

    private static SBPair newSBPair(SyntaxBuilder a, SyntaxBuilder b) {
        return new SBPair(a, b);
    }

    private static class SBPair {
        private final SyntaxBuilder fst, snd;

        public SBPair(SyntaxBuilder fst, SyntaxBuilder snd) {
            this.fst = fst;
            this.snd = snd;
        }


        public SyntaxBuilder getFst() {
            return fst;
        }

        public SyntaxBuilder getSnd() {
            return snd;
        }
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
                sb.beginRender();
                sb.addApplication("eq", newsb(last), newsb(next));
                sb.addSpace();
                sb.addKeyword("&&");
                sb.addSpace();
                sb.endRender();
                last = next;
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
