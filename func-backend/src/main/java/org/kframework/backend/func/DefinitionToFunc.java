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
import java.util.function.BiFunction;
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
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

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
import static org.kframework.backend.func.FuncUtil.*;

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
    private final SyntaxBuilder ocamlDefinition;
    private final SyntaxBuilder ocamlConstants;

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
        this.ocamlDefinition = genDefinition(preproc);
        this.ocamlConstants  = FuncConstants.genConstants(preproc);
        debugOutput(preproc);
    }

    public String getConstants() {
        return ocamlConstants.toString();
    }

    public String getDefinition() {
        return ocamlDefinition.toString();
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

    public String execute(K k, int depth, String outFile) {
        return executeSB(k, depth, outFile).toString();
    }

    public String match(K k, Rule r, String outFile) {
        return matchSB(k, r, outFile).toString();
    }

    public String executeAndMatch(K k, int depth, Rule r,
                                  String outFile,
                                  String substFile) {
        return executeAndMatchSB(k, depth, r, outFile, substFile).toString();
    }

    private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        sb.append(FuncConstants.genConstants(ppk));
        sb.append(addFunctions(ppk));
        sb.append(addSteps(ppk));
        sb.append(OCamlIncludes.postludeSB);

        return sb;
    }

    private SyntaxBuilder genImports() {
        return newsb()
            .addImport("Prelude")
            .addImport("Constants")
            .addImport("Prelude.K")
            .addImport("Gmp")
            .addImport("Def");
    }

    public SyntaxBuilder genDefinition(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        Module mm = ppk.mainModule;

        sb.append(genImports());
        SetMultimap<KLabel, Rule> functionRules = HashMultimap.create();
        ListMultimap<KLabel, Rule> anywhereRules = ArrayListMultimap.create();
        anywhereKLabels = new HashSet<>();
        for(Rule r : iterable(mm.rules())) {
            K left = RewriteToTop.toLeft(r.body());
            if(left instanceof KSequence) {
                KSequence kseq = (KSequence) left;
                if(kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                    KApply kapp = (KApply) kseq.items().get(0);
                    if(mm.attributesFor().apply(kapp.klabel()).contains(Attribute.FUNCTION_KEY)) {
                        functionRules.put(kapp.klabel(), r);
                    }
                }
            }
        }
        functions = new HashSet<>(functionRules.keySet());
        for(Production p : iterable(mm.productions())) {
            if(p.att().contains(Attribute.FUNCTION_KEY)) {
                functions.add(p.klabel().get());
            }
        }

        String conn = "let rec ";
        for(KLabel functionLabel : functions) {
            sb.append(conn);
            String functionName = encodeStringToFunction(functionLabel.name());
            sb.appendf("%s (c: k list) (config: k) (guards: Guard.t) : k = ",
                       functionName);
            sb.appendf("let lbl = %s and sort = %s",
                       encodeStringToIdentifier(functionLabel),
                       encodeStringToIdentifier(mm.sortFor()
                                                  .apply(functionLabel)));
            String sortHook = "";
            if(mm.attributesFor()
                 .apply(functionLabel)
                 .contains(Attribute.PREDICATE_KEY)) {
                Sort sort = Sort(mm.attributesFor()
                                   .apply(functionLabel)
                                   .<String>get(Attribute.PREDICATE_KEY)
                                   .get());
                sb.append(" and pred_sort = %s",
                          encodeStringToIdentifier(sort));
                if(mm.sortAttributesFor().contains(sort)) {
                    sortHook = mm.sortAttributesFor()
                                 .apply(sort)
                                 .<String>getOptional("hook")
                                 .orElse("");
                }
            }

            sb.append(" in match c with ");
            sb.append("\n");

            String hook = mm.attributesFor()
                            .apply(functionLabel)
                            .<String>getOptional(Attribute.HOOK_KEY)
                            .orElse(".");
            String namespace = hook.substring(0, hook.indexOf('.'));
            String function = hook.substring(namespace.length() + 1);

            if(hookNamespaces.contains(namespace)) {
                sb.append("| _ -> try ");
                sb.appendf("%s.hook_%s", namespace, function);
                sb.append(" c lbl sort config freshFunction\n");
                sb.append("with Not_implemented -> match c with \n");
            } else if(!hook.equals(".")) {
                kem.registerCompilerWarning("missing entry for hook " + hook);
            }

            if(predicateRules.containsKey(sortHook)) {
                sb.append("| ");
                sb.append(predicateRules.get(sortHook));
                sb.append("\n");
            }

            sb.append(convertFunction(functionRules.get(functionLabel)
                                                   .stream()
                                                   .sorted(this::sortFunctionRules)
                                                   .collect(toList()),
                                      functionName,
                                      RuleType.FUNCTION));
            sb.append("| _ -> raise (Stuck [KApply(lbl, c)])\n");
            conn = "and ";
        }


        for(KLabel functionLabel : anywhereKLabels) {
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
        for(Sort sort : iterable(mm.freshFunctionFor().keys())) {
            sb.append("| \"");
            sb.append(sort.name());
            sb.append("\" -> (");
            KLabel freshFunction = mm.freshFunctionFor().apply(sort);
            sb.append(encodeStringToFunction(freshFunction.name()));
            sb.append(" ([Int counter] :: []) config Guard.empty)\n");
        }


        sb.append("and eval (c: kitem) (config: k) : k = match c with KApply(lbl, kl) -> (match lbl with \n");
        for(KLabel label : Sets.union(functions, anywhereKLabels)) {
            sb.append("|");
            encodeStringToIdentifier(sb, label);
            sb.append(" -> ");
            encodeStringToFunction(sb, label.name());
            sb.append(" kl config Guard.empty\n");
        }


        sb.append("| _ -> [c])\n");
        sb.append("| _ -> [c]\n");
        sb.append("let rec lookups_step (c: k) (config: k) (guards: Guard.t) : k = match c with \n");
        List<Rule> sortedRules = stream(mm.rules())
                .sorted((r1, r2) -> ComparisonChain.start()
                        .compareTrueFirst(r1.att().contains("structural"), r2.att().contains("structural"))
                        .compareFalseFirst(r1.att().contains("owise"), r2.att().contains("owise"))
                        .compareFalseFirst(indexesPoorly(ppk, r1),
                                           indexesPoorly(ppk, r2))
                        .result())
                .filter(r -> !functionRules.values().contains(r) && !r.att().contains(Attribute.MACRO_KEY) && !r.att().contains(Attribute.ANYWHERE_KEY))
                .collect(Collectors.toList());
        Map<Boolean, List<Rule>> groupedByLookup = sortedRules.stream()
                .collect(Collectors.groupingBy(this::hasLookups));
        sb.append(convert(ppk,
                          groupedByLookup.get(true),
                          "lookups_step",
                          RuleType.REGULAR,
                          0));
        sb.append("| _ -> raise (Stuck c)\n");
        sb.append("let step (c: k) : k = let config = c in match c with \n");
        if(groupedByLookup.containsKey(false)) {
            for(Rule r : groupedByLookup.get(false)) {
                sb.append(convert(ppk, r, RuleType.REGULAR));
                if(fastCompilation) {
                    sb.append("| _ -> match c with \n");
                }
            }
        }
        sb.append("| _ -> lookups_step c c Guard.empty\n");
        sb.append(OCamlIncludes.postludeSB);
        return sb;
    }

    private SyntaxBuilder convertFunction(PreprocessedKORE ppk,
                                          List<Rule> rules,
                                          String functionName,
                                          RuleType type) {
        SyntaxBuilder sb = newsb();
        int ruleNum = 0;
        for(Rule r : rules) {
            if(hasLookups(r)) {
                Tuple2<Integer, SyntaxBuilder> pair;
                pair = convert(ppk, Collections.singletonList(r),
                               functionName, type, ruleNum);
                ruleNum = pair._1();
                sb.append(pair._2());
            } else {
                sb.append(convert(ppk, r, type));
            }
            if(fastCompilation) {
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
        HashSet<KLabel> functions = new HashSet<KLabel>(functionRules.keySet());
        for(KLabel lbl : functions) {
            mapping.put(lbl, counter++);
        }
        List<Integer>[] predecessors = new List[functions.size()];
        for(int i = 0; i < predecessors.length; i++) {
            predecessors[i] = new ArrayList<>();
        }

        class GetPredecessors extends VisitKORE {
            private final KLabel current;

            public GetPredecessors(KLabel current) {
                this.current = current;
            }

            @Override
            public Void apply(KApply k) {
                if(functions.contains(k.klabel())) {
                    predecessors[mapping.get(current)].add(mapping.get(k.klabel()));
                }
                return super.apply(k);
            }
        }

        for(Map.Entry<KLabel, Rule> entry : functionRules.entries()) {
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

    private <A,B,C,D> Tuple4<A,B,C,D> entryTuple(Map.Entry<Tuple3<A,B,C>,D> e) {
        Tuple3<A,B,C> k = e.getKey();
        return Tuple4.apply(k._1(), k._2(), k._3(), e.getValue());
    }

    private Tuple2<Integer, SyntaxBuilder> convert(PreprocessedKORE ppk,
                                                   List<Rule> rules,
                                                   String functionName,
                                                   RuleType ruleType,
                                                   int ruleNum) {
        SyntaxBuilder sb = newsb();

        NormalizeVariables t = new NormalizeVariables();

        Function<Rule, ?> groupingFunc = r -> {
            return new AttCompare(t.normalize(RewriteToTop.toLeft(r.body())),
                                  "sort");
        };

        Map<AttCompare, List<Rule>>
            grouping = rules.stream().collect(groupingBy(groupingFunc));

        Map<Tuple3<AttCompare, KLabel, AttCompare>, List<Rule>>
            groupByFirstPrefix = new HashMap<>();

        for(Map.Entry<AttCompare, List<Rule>> entry : grouping.entrySet()) {
            AttCompare left = entry.getKey();

            Function<Rule, ?> tempRule = r -> {
                KApply lookup = getLookup(r, 0);
                if(lookup == null) { return null; }
                //reconstruct the denormalization for this particular rule
                K left2 = t.normalize(RewriteToTop.toLeft(r.body()));
                K normal = t.normalize(t.applyNormalization(lookup.klist()
                                                                  .items()
                                                                  .get(1),
                                                            left2));
                return Tuple3.apply(left,
                                    lookup.klabel(),
                                    new AttCompare(normal, "sort"));
            };

            groupByFirstPrefix.putAll(entry.getValue()
                                           .stream()
                                           .collect(groupingBy(tempRule)));
        }

        List<Rule> owiseRules = newArrayList();

        BiFunction<?, ?, ?> sorter = (e1, e2) -> {
            return Integer.compare(e2.getValue().size(),
                                   e1.getValue().size());
        };

        for(Tuple4<AttCompare, KLabel, AttCompare, List<Rule>> entry2
                : groupByFirstPrefix.entrySet()
                                    .stream()
                                    .sorted(sorter)
                                    .map(entryTuple)
                                    .collect(toList())) {

            K left = entry2._1().get();
            VarInfo globalVars = new VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk, ruleType, left, globalVars));
            K lookup;
            sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", ruleNum);
            if(entry2._4().size() == 1) {
                Rule r = entry2._4().get(0);
                sb.append(convertComment(r));

                left = t.normalize(RewriteToTop.toLeft(r.body()));
                lookup = t.normalize(t.applyNormalization(getLookup(r, 0)
                                                          .klist()
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
                sb.append(convertRHS(ppk,
                                     ruleType,
                                     RewriteToTop.toRight(r.body()),
                                     globalVars,
                                     pair.suffix));
                ruleNum++;
            } else {
                KToken dummy = KToken("dummy", Sort("Dummy"));
                KApply kapp  = KApply(entry2._2(), dummy, entry2._3().get());
                List<Lookup> lookupList  = convertLookups(kapp,
                                                          globalVars,
                                                          functionName,
                                                          ruleNum++,
                                                          true);
                sb.append(lookupList.get(0).prefix);
                for(Rule r : entry2._4()) {
                    if(indexesPoorly(ppk, r) || r.att().contains("owise")) {
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
                        sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", ruleNum);
                        SBPair pair = convertSideCondition(r.requires(),
                                                           vars,
                                                           lookups,
                                                           lookups.size() > 0);
                        sb.append(pair.prefix);
                        sb.append(" -> ");
                        sb.append(convertRHS(ppk,
                                             ruleType,
                                             RewriteToTop.toRight(r.body()),
                                             vars,
                                             pair.suffix));
                        ruleNum++;
                        if(fastCompilation) {
                            sb.append("| _ -> match e with \n");
                        }
                    }
                }
                sb.append(lookupList.get(0).suffix);
                sb.append("\n");
            }
        }

        for(Rule r : owiseRules) {
            VarInfo globalVars = new VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk,
                                 ruleType,
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
            sb.append(convertRHS(ppk,
                                 ruleType,
                                 RewriteToTop.toRight(r.body()),
                                 globalVars,
                                 suffix));
            ruleNum++;
        }
        return new Tuple2(ruleNum, sb);
    }

    private boolean indexesPoorly(PreprocessedKORE ppk, Rule r) {
        Module mm = ppk.mainModule;
        class Holder { boolean b; }
        Holder h = new Holder();
        VisitKORE visitor = new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if(k.klabel().name().equals("<k>")) {
                    if(k.klist().items().size() == 1) {
                        if(k.klist().items().get(0) instanceof KSequence) {
                            KSequence kCell = (KSequence) k.klist().items().get(0);
                            if(kCell.items().size() == 2 && kCell.items().get(1) instanceof KVariable) {
                                if(kCell.items().get(0) instanceof KVariable) {
                                    Sort s = Sort(kCell.items().get(0).att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
                                    if(mm.sortAttributesFor().contains(s)) {
                                        String hook = mm.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
                                        if(!sortVarHooks.containsKey(hook)) {
                                            h.b = true;
                                        }
                                    } else {
                                        h.b = true;
                                    }
                                } else if(kCell.items().get(0) instanceof KApply) {
                                    KApply kapp = (KApply) kCell.items().get(0);
                                    if(kapp.klabel() instanceof KVariable) {
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
                if(h.i > idx) { return null; }
                if(k.klabel().name().equals("#match")
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

    private SyntaxBuilder convert(PreprocessedKORE ppk,
                                  Rule r,
                                  RuleType type) {
        try {
            SyntaxBuilder sb = newsb();
            sb.append(convertComment(r));
            sb.append("| ");
            K left = RewriteToTop.toLeft(r.body());
            K right = RewriteToTop.toRight(r.body());
            K requires = r.requires();
            VarInfo vars = new VarInfo();
            sb.append(convertLHS(ppk, type, left, vars));
            SyntaxBuilder prefix = convert(ppk, vars);
            SyntaxBuilder suffix = newsb();
            if(!requires.equals(KSequence(BooleanUtils.TRUE)) ||
               !prefix.equals("true")) {
                SBPair pair = convertSideCondition(requires,
                                                   vars,
                                                   Collections.emptyList(),
                                                   true);
                sb.append(pair.prefix);
                suffix.append(pair.suffix);
            }
            sb.append(" -> ");
            sb.append(convertRHS(ppk, type, right, vars, suffix));
            return new SBPair(prefix, suffix);
        } catch (KEMException e) {
            e.exception.addTraceFrame("while compiling rule at " + r.att().getOptional(Source.class).map(Object::toString).orElse("<none>") + ":" + r.att().getOptional(Location.class).map(Object::toString).orElse("<none>"));
            throw e;
        }
    }

    private SyntaxBuilder convertComment(Rule r) {
        return newsb()
            .addComment(String.format("rule %s requires %s ensures %s | %s",
                                      ToKast.apply(r.body()),
                                      ToKast.apply(r.requires()),
                                      ToKast.apply(r.ensures()),
                                      r.att()));
    }

    private SyntaxBuilder convertLHS(PreprocessedKORE ppk,
                                     RuleType type,
                                     K left,
                                     VarInfo vars) {
        FuncVisitor visitor = genVisitor(vars, false, false, false);
        if(type == RuleType.ANYWHERE || type == RuleType.FUNCTION) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            return visitor.apply(kapp.klist().items(), true);
        } else {
            return visitor.apply(left);
        }
    }

    private SyntaxBuilder convertRHS(PreprocessedKORE ppk,
                                     RuleType type,
                                     K right,
                                     VarInfo vars,
                                     SyntaxBuilder suffix) {
        SyntaxBuilder sb = newsb();
        boolean isPat = type == RuleType.PATTERN;
        boolean isAny = type == RuleType.ANYWHERE;

        if(isPat) {
            for(KVariable var : vars.vars.keySet()) {
                sb.beginApplication();
                sb.addFunction("Subst.add");
                sb.addArgument(newsbv(enquoteString(var.name())));
                String fmt = isList(var, vars, true, false) ? "%s" : "[%s]";
                sb.addArgument(newsbv(String.format(fmt, vars.vars
                                                             .get(var)
                                                             .iterator()
                                                             .next())));
            }
            sb.addArgument(newsbv("Subst.empty"));
            for(KVariable var : vars.vars.keySet()) {
                sb.endApplication();
            }
        } else {
            sb.append(genVisitor(vars, true, false, isAny).apply(right));
        }

        if(isAny) {
            sb = newsb().addMatch(sb,
                                  asList(newsbp("[item]")),
                                  asList(newsbApp("eval",
                                                  newsbr("item"),
                                                  newsbr("config"))));
        }

        sb.append(suffix);
        sb.addNewline();
        return sb;
    }

    private Tuple2<SyntaxBuilder,SyntaxBuilder> convertSideCondition(PreprocessedKORE ppk,
                                                                     K requires,
                                                                     VarInfo vars,
                                                                     List<Lookup> lus,
                                                                     boolean when) {
        SyntaxBuilder prefix, suffix;
        for(Lookup lookup : lus) {
            sb.append(lookup.prefix);
            sb.append(lookup.pattern);
        }
        prefix = convert(ppk, vars);
        sb.append(when ? " when " : " && ");
        sb.append(genVisitor(vars, true, true, false).apply(requires));
        sb.appendf(" && (%s)", result);
        suffix = Lists.reverse(lus)
                      .stream()
                      .map(l -> l.suffix)
                      .collect(joining()); // possible change in semantics
        return new Tuple2<SyntaxBuilder,SyntaxBuilder>(prefix, suffix);
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

    private List<Lookup> convertLookups(K requires,
                                        VarInfo vars,
                                        String functionName,
                                        int ruleNum,
                                        boolean hasMultiple) {
        List<Lookup> results = new ArrayList<>();
        Holder h = new Holder();
        h.first = hasMultiple;
        h.reapply = "(" + functionName + " c config (Guard.add (GuardElt.Guard " + ruleNum + ") guards))";
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {

                return super.apply(k);
            }

            private void choose(KApply k, String choiceString) {
                convertLookupsChoose(k, choiceString);
            }
        }.apply(requires);
        return results;
    }

    private void convertLookupsApply(KApply k) {
        if(k.klabel().name().equals("#match")) {
            if(k.klist().items().size() != 2) {
                throw kemInternalErrorF(k, "Unexpected arity of lookup: %s",
                                        k.klist().size());
            }
            SyntaxBuilder sb = newsb();
            sb.append(" -> (let e = ");
            sb.append(genVisitor(vars, true, false, false).apply(k.klist()
                                                                  .items()
                                                                  .get(1)));
            sb.append(" in match e with \n");
            sb.append("| [Bottom] -> ");
            sb.append(h.reapply);
            sb.append("\n");
            String prefix = sb.toString();
            sb = newsb();
            sb.append("| ");
            sb.append(genVisitor(vars, false, false, false).apply(k.klist()
                                                                   .items()
                                                                   .get(0)));
            String pattern = sb.toString();
            String suffix = "| _ -> " + h.reapply + ")";
            results.add(new Lookup(prefix, pattern, suffix));
            h.first = false;
        } else if(k.klabel().name().equals("#setChoice")) {
            convertLookupsChoose(k, "Set", "e");
        } else if(k.klabel().name().equals("#mapChoice")) {
            convertLookupsChoose(k, "Map", "e v");
        }
    }

    private void convertLookupsChoose(KApply k,
                                      int ruleNum,
                                      String collectionName,
                                      String collectionVars) {
        String formatString =
            "| [%s (_,_,collection)] -> let choice = (K%s.fold (fun %s result -> ";

        String choiceString = String.format(formatString,
                                            collectionName,
                                            collectionName,
                                            collectionVars);

        if(k.klist().items().size() != 2) {
            throw kemInternalErrorF(k, "Unexpected arity of choice: %s",
                                    k.klist().size());
        }

        SyntaxBuilder sb = newsb();
        sb.append(" -> (match ");
        sb.append(genVisitor(vars, true, false, false).apply(k.klist()
                                                              .items()
                                                              .get(1)));
        sb.append(" with \n");
        sb.append(choiceString);
        if(h.first) {
            sb.append("let rec stepElt = fun guards -> ");
        }
        sb.append("if (compare result [Bottom]) = 0 then (match e with ");
        String prefix = sb.toString();
        sb = newsb();
        String suffix2 = String.format("| _ -> [Bottom]) else result%s) collection [Bottom]) in if (compare choice [Bottom]) = 0 then %s else choice",
                                       (h.first ? " in stepElt Guard.empty" : ""),
                                       h.reapply);
        String suffix = String.format("%s| _ -> %s)", suffix2, h.reapply);
        if(h.first) {
            h.reapply = String.format("(stepElt (Guard.add (GuardElt.Guard %d) guards))",
                                      ruleNum);
        } else {
            h.reapply = "[Bottom]";
        }
        sb.append("| ");
        sb.append(genVisitor(vars, false, false, false).apply(k.klist()
                                                               .items()
                                                               .get(0)));
        String pattern = sb.toString();
        results.add(new Lookup(prefix, pattern, suffix));
        h.first = false;
    }

    private SyntaxBuilder convert(PreprocessedKORE ppk, VarInfo vars) {
        SyntaxBuilder sb = newsb();
        for(Map.Entry<KVariable, Collection<String>> entry : vars.vars
                                                                 .asMap()
                                                                 .entrySet()) {
            Collection<String> nonLinearVars = entry.getValue();
            if(nonLinearVars.size() < 2) { continue; }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                boolean il = isList(entry.getKey(), vars, true, false);
                sb.appendf("((%s %s %s) = 0) && ",
                           il ? "compare" : "compare_kitem",
                           applyVarRhs(ppk, last, sb, vars.listVars.get(last)),
                           applyVarRhs(ppk, next, sb, vars.listVars.get(next)));
                last = next;
            }
        }
        sb.append("true");
        return sb;
    }

    private SyntaxBuilder applyVarRhs(PreprocessedKORE ppk,
                                      KVariable v,
                                      VarInfo vars) {
        return applyVarRhs(ppk,
                           vars.vars.get(v).iterator().next(),
                           vars.listVars.get(vars.vars
                                                 .get(v)
                                                 .iterator()
                                                 .next()));
    }

    private SyntaxBuilder applyVarRhs(PreprocessedKORE ppk,
                                      String varOccurrance,
                                      KLabel listVar) {
        if(listVar != null) {
            return newsbf("(List (%s, %s, %s))",
                          encodeStringToIdentifier(ppk.mainModule
                                                      .sortFor()
                                                      .apply(listVar)),
                          encodeStringToIdentifier(listVar),
                          varOccurrance);
        } else {
            return newsb(varOccurrance);
        }
    }

    private SyntaxBuilder applyVarLhs(PreprocessedKORE ppk,
                                      KVariable k,
                                      VarInfo vars) {
        Module mm = ppk.mainModule;
        String varName = encodeStringToVariable(k.name());
        vars.vars.put(k, varName);
        Sort s = Sort(k.att()
                       .<String>getOptional(Attribute.SORT_KEY)
                       .orElse(""));
        SyntaxBilder res;
        if(mm.sortAttributesFor().contains(s)) {
            String hook = mm.sortAttributesFor()
                            .apply(s)
                            .<String>getOptional("hook")
                            .orElse("");
            if(sortVarHooks.containsKey(hook)) {
                res = newsbf("(%s as %s)",
                             sortVarHooks.get(hook).apply(s),
                             varName);
            } else {
                res = newsb(varName);
            }
        }

        return res;
    }

    /**
     * Create a Visitor based on the given information
     */
    private FuncVisitor genVisitor(VarInfo vars,
                               boolean rhs,
                               boolean useNativeBool,
                               boolean anywhereRule) {
        return new Visitor(rhs, vars, useNativeBool, anywhereRule);
    }

    public static String getSortOfVar(KVariable k, VarInfo vars) {
        if(vars.vars.containsKey(k)) {
            String varName = vars.vars.get(k).iterator().next();
            if(vars.listVars.containsKey(varName)) {
                return vars.listVars.get(varName).name();
            }
        }
        return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
    }

    /**
     * Does the given KApply have a lookup-related label?
     * @param k     The KApply to test
     * @return      Whether or not the given KApply has a lookup-related label
     */
    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match")
            || k.klabel().name().equals("#mapChoice")
            || k.klabel().name().equals("#setChoice");
    }

    /**
     * Determine whether the given item is a list, subject to the
     * extra information given in the remaining parameters.
     * @param item  The item to test for list-ness
     * @param vars  Information about variables
     * @param rhs   Are we on the right hand side of a KRewrite?
     * @param any   Is the current rule an [anywhere] rule?
     * @return      Whether or not item represents a list
     */
    private boolean isList(K item,
                           VarInfo vars,
                           boolean rhs,
                           boolean any) {
        // I'm using Supplier<Boolean> to retain the lazy
        // semantics of && and || in Java without having
        // a terrifyingly long boolean expression to read

        // There is some overhead with making these objects
        // but it shouldn't be too terrible

        Supplier<Boolean> itemKVar =
            () -> getSortOfVar((KVariable) item, vars).equals("K");

        Supplier<Boolean> itemKSeq = () -> true;

        Supplier<Boolean> itemKApp = () -> {
            KLabel kl = ((KApply) item).klabel();
            return functions.contains(kapp.klabel())
                || (rhs && ((anywhereKLabels.contains(kl) && !anywhereRule)
                            || kl instanceof KVariable));
        };

        Supplier<Boolean> valA =
            () -> (item instanceof KVariable && itemKVar.get())
               || (item instanceof KSequence && itemKSeq.get())
               || (item instanceof KApply    && itemKApp.get());

        Supplier<Boolean> valB =
            () -> rhs
               || !(item instanceof KApply)
               || !collectionFor.containsKey(((KApply) item).klabel());

        return valA.get() && valB.get();
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

//     /**
//      * Flag that determines whether or not we annotate output OCaml with rules
//      */
//     public static final boolean annotateOutput = false;

//     /**
//      * Flag that determines whether or not we output debug info
//      */
//     public static final boolean debugEnabled = true;


//     private final KExceptionManager kem;
//     private final SyntaxBuilder ocamlDef;

//     private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
//     private static final SyntaxBuilder wildcardSB, bottomSB, choiceSB, resultSB;

//     static {
//         wildcardSB = newsbv("_");
//         bottomSB = newsbv("[Bottom]");
//         choiceSB = newsbv("choice");
//         resultSB = newsbv("result");
//         setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
//                                  "e", "result");
//         mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
//                                  "k", "v", "result");
//     }

//     /**
//      * Constructor for DefinitionToFunc
//      */
//     public DefinitionToFunc(KExceptionManager kem,
//                             PreprocessedKORE preproc) {
//         this.kem = kem;
//         this.ocamlDef = langDefToFunc(preproc);
//         debugOutput(preproc);
//     }

//     public String genOCaml() {
//         return ocamlDef.toString();
//     }

//     private void debugOutput(PreprocessedKORE ppk) {
//         outprintfln("");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln("");

//         outprintfln(";; %s", ocamlDef.trackPrint()
//                                      .replaceAll("\n", "\n;; "));
//         outprintfln(";; Number of parens: %d", ocamlDef.getNumParens());
//         outprintfln(";; Number of lines:  %d", ocamlDef.getNumLines());


//         outprintfln("functionSet: %s", ppk.functionSet);
//         outprintfln("anywhereSet: %s", ppk.anywhereSet);

//         XMLBuilder outXML = ocamlDef.getXML();


//         outprintfln("");

//         try {
//             outprintfln("%s", outXML.renderSExpr());
//         } catch(KEMException e) {
//             outprintfln(";; %s", outXML.toString()
//                         .replaceAll("><", ">\n<")
//                         .replaceAll("\n", "\n;; "));
//         }


//         outprintfln("");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln("");
//     }

//     private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.append(FuncConstants.genConstants(ppk));
//         sb.append(addFunctions(ppk));
//         sb.append(addSteps(ppk));
//         sb.append(OCamlIncludes.postludeSB);

//         return sb;
//     }

//     private SyntaxBuilder addFunctionMatch(String functionName,
//                                            KLabel functionLabel,
//                                            PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();
//         String hook = ppk.attrLabels
//                          .get(Attribute.HOOK_KEY)
//                          .getOrDefault(functionLabel, "");
//         String fn = functionLabel.name();
//         boolean isHook = OCamlIncludes.hooks.containsKey(hook);
//         boolean isPred = OCamlIncludes.predicateRules.containsKey(fn);
//         Collection<Rule> rules = ppk.functionRulesOrdered
//                                     .getOrDefault(functionLabel, newArrayList());

//         if(!isHook && !hook.isEmpty()) {
//             kem.registerCompilerWarning("missing entry for hook " + hook);
//         }

//         sb.beginMatchExpression(newsbv("c"));

//         if(isHook) {
//             sb.append(OCamlIncludes.hooks.get(hook));
//         }

//         if(isPred) {
//             sb.append(OCamlIncludes.predicateRules.get(fn));
//         }

//         int i = 0;
//         for(Rule r : rules) {
//             sb.append(oldConvert(ppk, r, true, i++, functionName));
//         }

//         sb.addMatchEquation(wildcardSB, raiseStuck(newsbv("[KApply(lbl, c)]")));
//         sb.endMatchExpression();

//         return sb;
//     }

//     private SyntaxBuilder addFunctionEquation(KLabel functionLabel,
//                                               PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         String functionName = encodeStringToFunction(functionLabel.name());

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb()
//                                  .beginRender()
//                                  .addValue(functionName)
//                                  .addSpace()
//                                  .addValue("(c: k list)")
//                                  .addSpace()
//                                  .addValue("(guards: Guard.t)")
//                                  .addSpace()
//                                  .addKeyword(":")
//                                  .addSpace()
//                                  .addValue("k")
//                                  .endRender());
//         sb.beginLetrecEquationValue();
//         sb.beginLetExpression();
//         sb.beginLetDefinitions();
//         sb.addLetEquation(newsb("lbl"),
//                           newsb(encodeStringToIdentifier(functionLabel)));
//         sb.endLetDefinitions();

//         sb.beginLetScope();
//         sb.append(addFunctionMatch(functionName, functionLabel, ppk));
//         sb.endLetScope();

//         sb.endLetExpression();
//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addFreshFunction(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb()
//                                  .beginRender()
//                                  .addValue("freshFunction")
//                                  .addSpace()
//                                  .addValue("(sort: string)")
//                                  .addSpace()
//                                  .addValue("(counter: Z.t)")
//                                  .addSpace()
//                                  .addKeyword(":")
//                                  .addSpace()
//                                  .addValue("k")
//                                  .endRender());
//         sb.beginLetrecEquationValue();

//         sb.beginMatchExpression(newsb("sort"));
//         for(Sort sort : ppk.freshFunctionFor.keySet()) {
//             KLabel freshFunction = ppk.freshFunctionFor.get(sort);
//             sb.addMatchEquation(newsbf("\"%s\"",
//                                        sort.name()),
//                                 newsbf("(%s ([Int counter] :: []) Guard.empty)",
//                                        encodeStringToFunction(freshFunction.name())));
//         }
//         sb.endMatchExpression();

//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addEval(Set<KLabel> labels,
//                                   PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb("eval (c: kitem) : k"));
//         sb.beginLetrecEquationValue();

//         sb.beginMatchExpression(newsb("c"));

//         sb.beginMatchEquation();
//         sb.addMatchEquationPattern(newsb()
//                                    .addApplication("KApply",
//                                                    newsb("(lbl, kl)")));
//         sb.beginMatchEquationValue();
//         sb.beginMatchExpression(newsb("lbl"));
//         for(KLabel label : labels) {
//             SyntaxBuilder valSB =
//                 newsb().addApplication(encodeStringToFunction(label.name()),
//                                        newsb("kl"),
//                                        newsb("Guard.empty"));
//             sb.addMatchEquation(newsb(encodeStringToIdentifier(label)),
//                                 valSB);
//         }
//         sb.endMatchExpression();
//         sb.endMatchEquationValue();
//         sb.endMatchEquation();

//         sb.addMatchEquation(wildcardSB, newsbv("[c]"));

//         sb.endMatchExpression();

//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addFunctions(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         Set<KLabel> functions = ppk.functionSet;
//         Set<KLabel> anywheres = ppk.anywhereSet;

//         Set<KLabel> funcAndAny = Sets.union(functions, anywheres);


//         for(List<KLabel> component : ppk.functionOrder) {
//             sb.beginLetrecDeclaration();
//             sb.beginLetrecDefinitions();
//             for(KLabel functionLabel : component) {
//                 sb.append(addFunctionEquation(functionLabel, ppk));
//             }
//             sb.endLetrecDefinitions();
//             sb.endLetrecDeclaration();
//         }

//         sb.beginLetrecDeclaration();
//         sb.beginLetrecDefinitions();

//         sb.append(addFreshFunction(ppk));

//         sb.append(addEval(funcAndAny, ppk));

//         sb.endLetrecDefinitions();
//         sb.endLetrecDeclaration();

//         return sb;
//     }

//     private SyntaxBuilder makeStuck(SyntaxBuilder body) {
//         return newsb().addApplication("Stuck", body);
//     }

//     private SyntaxBuilder raiseStuck(SyntaxBuilder body) {
//         return newsb().addApplication("raise", makeStuck(body));
//     }

//     private SyntaxBuilder addSteps(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecDeclaration();
//         sb.beginLetrecDefinitions();
//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb("lookups_step (c: k) (guards: Guard.t) : k"));
//         sb.beginLetrecEquationValue();
//         sb.beginMatchExpression(newsb("c"));

//         int i = 0;
//         for(Rule r : ppk.indexedRules.keySet()) {
//             Set<String> cap = ppk.indexedRules.get(r);
//             if(cap.contains("lookup") && !cap.contains("function")) {
//                 sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "lookups_step")));
//             }
//         }

//         sb.addMatchEquation(wildcardSB, raiseStuck(newsb("c")));
//         sb.endMatchExpression();
//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();
//         sb.endLetrecDefinitions();
//         sb.endLetrecDeclaration();

//         sb.beginLetDeclaration();
//         sb.beginLetDefinitions();
//         sb.beginLetEquation();
//         sb.addLetEquationName(newsb("step (c: k) : k"));
//         sb.beginLetEquationValue();
//         sb.beginMatchExpression(newsb("c"));
//         for(Rule r : ppk.indexedRules.keySet()) {
//             Set<String> cap = ppk.indexedRules.get(r);
//             if(!cap.contains("lookup") && !cap.contains("function")) {
//                 sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "step")));
//             }
//         }
//         sb.addMatchEquation(newsb("_"),
//                             newsb("lookups_step c Guard.empty"));
//         sb.endMatchExpression();
//         sb.endLetEquationValue();
//         sb.endLetEquation();
//         sb.endLetDefinitions();
//         sb.endLetDeclaration();

//         return sb;
//     }

//     private SyntaxBuilder debugMismatch(SyntaxBuilder sb) {
//         Pattern xmlBegPat = Pattern.compile("<[^<>/]*>");
//         Pattern xmlEndPat = Pattern.compile("</[^<>/]*>");
//         Map<String, Integer> sbMap = sb.getTrack();
//         List<String> sbKeys = sbMap.keySet()
//                                    .stream()
//                                    .filter(x -> xmlEndPat.matcher(x).matches())
//                                    .map(x -> x.substring(2, x.length() - 1))
//                                    .collect(toList());
//         Map<String, Integer> mismatched = newHashMap();
//         for(String k : sbKeys) {
//             int begin = sbMap.get("<"  + k + ">").intValue();
//             int end   = sbMap.get("</" + k + ">").intValue();
//             if(begin != end) { mismatched.put(k, begin - end); }
//         }
//         if(! mismatched.isEmpty()) {
//             outprintfln(";; ---------------- ERROR ----------------");
//             outprintfln(";; The following were mismatched:");
//             for(String k : mismatched.keySet()) {
//                 outprintfln(";; %30s --> %s", k, mismatched.get(k));
//             }
//             outprintfln(";; ----------------");
//             outprintfln(";; XML:\n;; %s",
//                         sb.pretty().stream().collect(joining("\n;; ")));
//             outprintfln(";; ---------------- ERROR ----------------");
//         }
//         return sb;
//     }

//     private SyntaxBuilder outputAnnotate(Rule r) {
//         SyntaxBuilder sb = newsb();

//         sb.beginMultilineComment();
//         sb.appendf("rule %s requires %s ensures %s %s",
//                    ToKast.apply(r.body()),
//                    ToKast.apply(r.requires()),
//                    ToKast.apply(r.ensures()),
//                    r.att().toString());
//         sb.endMultilineComment();
//         sb.addNewline();

//         return sb;
//     }

//     private SyntaxBuilder unhandledOldConvert(PreprocessedKORE ppk,
//                                               Rule r,
//                                               boolean isFunction,
//                                               int ruleNum,
//                                               String functionName) throws KEMException {
//         SyntaxBuilder sb = newsb();

//         if(annotateOutput) { sb.append(outputAnnotate(r)); }

//         K left     = RewriteToTop.toLeft(r.body());
//         K right    = RewriteToTop.toRight(r.body());
//         K requires = r.requires();

//         Set<String> indices = ppk.indexedRules.get(r);
//         SetMultimap<KVariable, String> vars = HashMultimap.create();
//         FuncVisitor visitor = oldConvert(ppk, false, vars, false);

//         sb.beginMatchEquation();
//         sb.beginMatchEquationPattern();
//         sb.append(handleLeft(isFunction, left, visitor));

//         sb.append(handleLookup(indices, ruleNum));

//         SBPair side = handleSideCondition(ppk, vars, functionName, ruleNum, requires);

//         sb.append(side.getFst());
//         sb.endMatchEquationPattern();
//         sb.beginMatchEquationValue();
//         sb.append(oldConvert(ppk, true, vars, false).apply(right));

//         sb.endMatchEquationValue();
//         sb.endMatchEquation();
//         sb.append(side.getSnd());

//         return sb;
//     }

//     private SyntaxBuilder handleLeft(boolean isFunction,
//                                      K left,
//                                      FuncVisitor visitor) {
//         if(isFunction) {
//             return handleFunction(left, visitor);
//         } else {
//             return handleNonFunction(left, visitor);
//         }
//     }

//     private SyntaxBuilder handleFunction(K left, FuncVisitor visitor) {
//         KApply kapp = (KApply) ((KSequence) left).items().get(0);
//         return visitor.apply(kapp.klist().items(), true);
//     }

//     private SyntaxBuilder handleNonFunction(K left, FuncVisitor visitor) {
//         return visitor.apply(left);
//     }

//     private SyntaxBuilder handleLookup(Set<String> indices, int ruleNum) {
//         if(indices.contains("lookup")) {
//             return newsb()
//                 .beginRender()
//                 .addSpace()
//                 .addKeyword("when")
//                 .addSpace()
//                 .addKeyword("not")
//                 .addSpace()
//                 .beginApplication()
//                 .addFunction("Guard.mem")
//                 .beginArgument()
//                 .addApplication("GuardElt.Guard", newsbf("%d", ruleNum))
//                 .endArgument()
//                 .addArgument(newsb("guards"))
//                 .endApplication()
//                 .endRender();
//         } else {
//             return newsb();
//         }
//     }

//     private SBPair handleSideCondition(PreprocessedKORE ppk,
//                                        SetMultimap<KVariable, String> vars,
//                                        String functionName,
//                                        int ruleNum,
//                                        K requires) {
//          SBPair convLookups = oldConvertLookups(ppk, requires, vars,
//                                                functionName, ruleNum);

//          SyntaxBuilder result = oldConvert(vars);

//          if(hasSideCondition(requires, result.toString())) {
//              SyntaxBuilder fstSB =
//                  convLookups
//                  .getFst()
//                  .beginRender()
//                  .addSpace()
//                  .addKeyword("when")
//                  .addSpace()
//                  .beginRender()
//                  .append(oldConvert(ppk, true, vars, true).apply(requires))
//                  .endRender()
//                  .addSpace()
//                  .addKeyword("&&")
//                  .addSpace()
//                  .beginParenthesis()
//                  .beginRender()
//                  .append(result)
//                  .endRender()
//                  .endParenthesis()
//                  .endRender();
//              SyntaxBuilder sndSB = convLookups.getSnd();
//              return newSBPair(fstSB, sndSB);
//          } else {
//              return newSBPair(newsb(), newsb());
//          }
//     }

//     private boolean hasSideCondition(K requires, String result) {
//         return !(KSequence(BooleanUtils.TRUE).equals(requires))
//             || !("true".equals(result));
//     }

//     private SyntaxBuilder oldConvert(PreprocessedKORE ppk,
//                                      Rule r,
//                                      boolean function,
//                                      int ruleNum,
//                                      String functionName) {
//         try {
//             return unhandledOldConvert(ppk, r, function, ruleNum, functionName);
//         } catch (KEMException e) {
//             String src = r.att()
//                           .getOptional(Source.class)
//                           .map(Object::toString)
//                           .orElse("<none>");
//             String loc = r.att()
//                           .getOptional(Location.class)
//                           .map(Object::toString)
//                           .orElse("<none>");
//             e.exception
//              .addTraceFrame(String.format("while compiling rule at %s: %s",
//                                           src, loc));
//             throw e;
//         }
//     }

//     private void checkApplyArity(KApply k,
//                                  int arity,
//                                  String funcName) throws KEMException {
//         if(k.klist().size() != arity) {
//             throw kemInternalErrorF(k,
//                                     "Unexpected arity of %s: %s",
//                                     funcName,
//                                     k.klist().size());
//         }
//     }

//     private static final SyntaxBuilder choiceSB1(String chChoiceVar,
//                                                  String chFold,
//                                                  String chPat,
//                                                  String... chArgs) {
//         return newsb()
//             .beginMatchEquation()
//             .addMatchEquationPattern(newsbv(chPat))
//             .beginMatchEquationValue()
//             .beginLetExpression()
//             .beginLetDefinitions()
//             .beginLetEquation()
//             .addLetEquationName(choiceSB)
//             .beginLetEquationValue()
//             .beginApplication()
//             .addFunction(chFold)
//             .beginArgument()
//             .beginLambda(chArgs)
//             .beginConditional()
//             .addConditionalIf()
//             .append(newsb().addApplication("eq", resultSB, bottomSB))
//             .addConditionalThen()
//             .beginMatchExpression(newsbv(chChoiceVar));
//     }


//     private static final SyntaxBuilder choiceSB2(String chVar,
//                                                  int ruleNum,
//                                                  String functionName) {
//         String guardCon = "GuardElt.Guard";
//         String guardAdd = "Guard.add";
//         SyntaxBuilder rnsb = newsbv(Integer.toString(ruleNum));

//         SyntaxBuilder guardSB =
//             newsb().addApplication(guardAdd,
//                                    newsb().addApplication(guardCon, rnsb),
//                                    newsbv("guards"));
//         SyntaxBuilder condSB =
//             newsb().addConditional(newsb().addApplication("eq", choiceSB, bottomSB),
//                                    newsb().addApplication(functionName,
//                                                           newsbv("c"),
//                                                           guardSB),
//                                    choiceSB);

//         return newsb()
//             .addMatchEquation(wildcardSB, bottomSB)
//             .endMatchExpression()
//             .addConditionalElse()
//             .append(resultSB)
//             .endConditional()
//             .endLambda()
//             .endArgument()
//             .addArgument(newsbv(chVar))
//             .addArgument(bottomSB)
//             .endApplication()
//             .endLetEquationValue()
//             .endLetEquation()
//             .endLetDefinitions()
//             .addLetScope(condSB)
//             .endLetExpression()
//             .endMatchEquationValue()
//             .endMatchEquation();
//     }

//     // TODO(remy): this needs refactoring very badly
//     private SBPair oldConvertLookups(PreprocessedKORE ppk,
//                                      K requires,
//                                      SetMultimap<KVariable, String> vars,
//                                      String functionName,
//                                      int ruleNum) {
//         Deque<SyntaxBuilder> suffStack = new ArrayDeque<>();

//         SyntaxBuilder res = newsb();
//         SyntaxBuilder setChoiceSB2 = choiceSB2("s", ruleNum, functionName);
//         SyntaxBuilder mapChoiceSB2 = choiceSB2("m", ruleNum, functionName);
//         String formatSB3 = "(%s c (Guard.add (GuardElt.Guard %d) guards))";
//         SyntaxBuilder sb3 =
//             newsb().addMatchEquation(wildcardSB, newsbf(formatSB3,
//                                                         functionName,
//                                                         ruleNum))
//                    .endMatchExpression()
//                    .endMatchEquationValue()
//                    .endMatchEquation();

//         new VisitKORE() {
//             private SyntaxBuilder sb1;
//             private SyntaxBuilder sb2;
//             private String functionStr;
//             private int arity;

//             @Override
//             public Void apply(KApply k) {
//                 List<K> kitems = k.klist().items();
//                 String klabel = k.klabel().name();

//                 boolean choiceOrMatch = true;

//                 switch(klabel) {
//                 case "#match":
//                     functionStr = "lookup";
//                     sb1 = newsb();
//                     sb2 = newsb();
//                     arity = 2;
//                     break;
//                 case "#setChoice":
//                     functionStr = "set choice";
//                     sb1 = setChoiceSB1;
//                     sb2 = setChoiceSB2;
//                     arity = 2;
//                     break;
//                 case "#mapChoice":
//                     functionStr = "map choice";
//                     sb1 = mapChoiceSB1;
//                     sb2 = mapChoiceSB2;
//                     arity = 2;
//                     break;
//                 default:
//                     choiceOrMatch = false;
//                     break;
//                 }

//                 if(choiceOrMatch) {
//                     // prettyStackTrace();

//                     checkApplyArity(k, arity, functionStr);

//                     K kLabel1 = kitems.get(0);
//                     K kLabel2 = kitems.get(1);

//                     SyntaxBuilder luMatchValue
//                         = oldConvert(ppk, true,  vars, false).apply(kLabel2);
//                     SyntaxBuilder luLevelUp     = sb1;
//                     SyntaxBuilder luPattern
//                         = oldConvert(ppk, false, vars, false).apply(kLabel1);
//                     SyntaxBuilder luWildcardEqn = sb3;
//                     SyntaxBuilder luLevelDown   = sb2;

//                     res.endMatchEquationPattern();
//                     res.beginMatchEquationValue();
//                     res.beginMatchExpression(luMatchValue);
//                     res.append(luLevelUp);
//                     res.beginMatchEquation();
//                     res.beginMatchEquationPattern();
//                     res.append(luPattern);

//                     suffStack.add(luWildcardEqn);
//                     suffStack.add(luLevelDown);
//                 }

//                 return super.apply(k);
//             }
//         }.apply(requires);

//         SyntaxBuilder suffSB = newsb();
//         while(!suffStack.isEmpty()) { suffSB.append(suffStack.pollLast()); }

//         return newSBPair(res, suffSB);
//     }

//     private static String prettyStackTrace() {
//         Pattern funcPat = Pattern.compile("^org[.]kframework.*$");

//         SyntaxBuilder sb = newsb();
//         sb.append(";; DBG: ----------------------------\n");
//         sb.append(";; DBG: apply executed:\n");
//         StackTraceElement[] traceArray = Thread.currentThread().getStackTrace();
//         List<StackTraceElement> trace = newArrayList(traceArray.length);

//         for(StackTraceElement ste : traceArray) { trace.add(ste); }

//         trace = trace
//             .stream()
//             .filter(x -> funcPat.matcher(x.toString()).matches())
//             .collect(toList());

//         int skip = 1;
//         for(StackTraceElement ste : trace) {
//             if(skip > 0) {
//                 skip--;
//             } else {
//                 sb.appendf(";; DBG: trace: %20s %6s %30s\n",
//                            ste.getMethodName(),
//                            "(" + Integer.toString(ste.getLineNumber()) + ")",
//                            "(" + ste.getFileName() + ")");
//             }
//         }
//         return sb.toString();
//     }

//     private static SBPair newSBPair(SyntaxBuilder a, SyntaxBuilder b) {
//         return new SBPair(a, b);
//     }

//     private static class SBPair {
//         private final SyntaxBuilder fst, snd;

//         public SBPair(SyntaxBuilder fst, SyntaxBuilder snd) {
//             this.fst = fst;
//             this.snd = snd;
//         }


//         public SyntaxBuilder getFst() {
//             return fst;
//         }

//         public SyntaxBuilder getSnd() {
//             return snd;
//         }
//     }

//     private static SyntaxBuilder oldConvert(SetMultimap<KVariable, String> vars) {
//         SyntaxBuilder sb = newsb();
//         for(Collection<String> nonLinearVars : vars.asMap().values()) {
//             if(nonLinearVars.size() < 2) { continue; }
//             Iterator<String> iter = nonLinearVars.iterator();
//             String last = iter.next();
//             while (iter.hasNext()) {
//                 //handle nonlinear variables in pattern
//                 String next = iter.next();
//                 sb.beginRender();
//                 sb.addApplication("eq", newsb(last), newsb(next));
//                 sb.addSpace();
//                 sb.addKeyword("&&");
//                 sb.addSpace();
//                 sb.endRender();
//                 last = next;
//             }
//         }
//         sb.addValue("true");
//         return sb;
//     }

//     private FuncVisitor oldConvert(PreprocessedKORE ppk,
//                                    boolean rhs,
//                                    SetMultimap<KVariable, String> vars,
//                                    boolean useNativeBooleanExp) {
//         return new FuncVisitor(ppk, rhs, vars, useNativeBooleanExp);
//     }
