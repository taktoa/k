// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;

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
import java.util.Queue;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Comparator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

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
import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Main class for converting KORE to functional code
 *
 * @author Remy Goldschmidt
 */
public class DefinitionToFunc {
    public static final boolean ocamlopt = false;
    public static final boolean commentsEnabled = false;
    public static final Pattern identChar = Pattern.compile("[A-Za-z0-9_]");

    private final KExceptionManager kem;
    private final SyntaxBuilder ocamlDefinition;
    private final SyntaxBuilder ocamlConstants;

    // private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
    //
    // static {
    //     setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
    //                              "e", "result");
    //     mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
    //                              "k", "v", "result");
    // }

    /**
     * Constructor for DefinitionToFunc
     */
    public DefinitionToFunc(PreprocessedKORE preproc,
                            KExceptionManager kem) {
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

        outprintfln(";; %s", ocamlDefinition.trackPrint()
                                            .replaceAll(newline(), newline() + ";; "));
        outprintfln(";; Number of parens: %d", ocamlDefinition.getNumParens());
        outprintfln(";; Number of lines:  %d", ocamlDefinition.getNumLines());

        XMLBuilder outXML = ocamlDefinition.getXML();

        outprintfln("");

        try {
            outprintfln("%s", outXML.renderSExpr());
        } catch(KEMException e) {
            outprintfln(";; %s", outXML.toString()
                        .replaceAll("><", ">" + newline() + "<")
                        .replaceAll(newline(), newline() + ";; "));
        }

        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");
    }

    // DOES NOT COMPILE
    public SyntaxBuilder genDefinition(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.addImport("Prelude");
        sb.addImport("Constants");
        sb.addImport("Constants.K");

        SetMultimap<KLabel, Rule> rules = HashMultimap.create();
        rules.putAll(ppk.functionRules);
        rules.putAll(ppk.anywhereRules);

        FunctionOrder functionOrder = FunctionOrder.generateOrder(ppk);

        //compute fixed point. The only hook that actually requires this argument is KREFLECTION.fresh, so we will automatically
        //add the real definition of this function before we declare any function that requires it.
        sb.append("let freshFunction (sort: string) (config: k) (counter: Z.t) : k = [Bottom]");

        Predicate<KLabel> isImpure = kl -> ppk.attributesFor
                                              .get(kl)
                                              .contains(Attribute.IMPURE_KEY);

        Set<KLabel> impurities =
            ppk.functionSet
               .stream()
               .filter(isImpure)
               .collect(toSetC());

        impurities.addAll(ancestors(impurities,
                                    functionOrder.getDependencies()));

        Predicate<KLabel> isConstant = kl -> {
            if(impurities.contains(kl)) { return false; }

            return ppk.productionsFor
                      .get(kl)
                      .stream()
                      .filter(p -> p.arity() == 0)
                      .findAny()
                      .isPresent();
        };

        Set<KLabel> constantLabels = filterCollS(isConstant, ppk.functionSet);

        Function<KLabel, Att> attFor = ppk.attributesFor::get;

        for(List<KLabel> component : functionOrder.getOrder()) {
            String conn;
            if(component.size() == 1) {
                MutableBoolean isRecursive = new MutableBoolean(false);
                for(Rule r : rules.get(component.get(0))) {
                    class ComputeRecursion extends VisitKORE {
                        @Override
                        public Void apply(KApply k) {
                            if(k.klabel().equals(component.get(0))) {
                                isRecursive.setTrue();
                            }
                            return super.apply(k);
                        }
                    }
                    new ComputeRecursion().apply(RewriteToTop.toRight(r.body()));
                    new ComputeRecursion().apply(r.requires());
                    if(hasLookups(r)) {
                        isRecursive.setTrue();
                    }
                }
                if(isRecursive.getValue()) {
                    conn = "let rec ";
                } else {
                    conn = "let ";
                }
            } else {
                conn = "let rec ";
            }
            for(KLabel functionLabel : component) {
                String hook = attFor.apply(functionLabel)
                                    .<String>getOptional(Attribute.HOOK_KEY)
                                    .orElse(".");
                if(hook.equals("KREFLECTION.fresh")) {
                    sb.append("let freshFunction (sort: string) (config: k) (counter: Z.t) : k = match sort with \n");
                    for(Sort sort : ppk.freshFunctionFor.keySet()) {
                        sb.append("| \"");
                        sb.append(sort.name());
                        sb.append("\" -> (");
                        KLabel freshFunction = ppk.freshFunctionFor.get(sort);
                        sb.append(encodeStringToFunction(freshFunction));
                        sb.append(" ([Int counter]) config (-1))\n");
                    }
                }
                if(ppk.functionSet.contains(functionLabel)) {
                    sb.append(conn);
                    String functionName = encodeStringToFunction(functionLabel);
                    sb.append(functionName);
                    int arity = getArity(ppk, functionLabel);
                    sb.append(printFunctionParams(arity));
                    sb.append(" (config: k) (guard: int) : k = let lbl = \n");
                    sb.append(encodeStringToIdentifier(functionLabel));
                    sb.append(" and sort = \n");
                    sb.append(encodeStringToIdentifier(ppk.sortFor.get(functionLabel)));
                    sb.append(" in ");
                    sb.append("match c with \n");
                    String namespace = hook.substring(0, hook.indexOf('.'));
                    String function = hook.substring(namespace.length() + 1);
                    if(      OCamlIncludes.hookNamespaces.contains(namespace)
                       || ppk.options.getHookNamespaces().contains(namespace)) {
                        sb.append("| _ -> try ");
                        sb.append(namespace);
                        sb.append(".hook_");
                        sb.append(function);
                        sb.append(" c lbl sort config freshFunction");
                        if(attFor.apply(functionLabel)
                                 .contains("canTakeSteps")) {
                            sb.append(" eval");
                        }
                        sb.append("\nwith Not_implemented -> match c with \n");
                    } else if(!hook.equals(".")) {
                        kem.registerCompilerWarning("missing entry for hook " + hook);
                    }

                    if(attFor.apply(functionLabel)
                             .contains(Attribute.PREDICATE_KEY)) {
                        Sort pSort = Sort(attFor.apply(functionLabel)
                                                .<String>get(Attribute.PREDICATE_KEY)
                                                .get());
                        ppk.definedSorts
                            .stream()
                            .filter(s -> ppk.subsorts.greaterThanEq(pSort, s))
                            .distinct()
                            .filter(ppk.sortAttributesFor.keySet()::contains)
                            .forEach(sort -> {
                            String sortHook = ppk.sortAttributesFor
                                                 .get(sort)
                                                 .<String>getOptional("hook")
                                                 .orElse("");
                            if(predicateRules.containsKey(sortHook)) {
                                sb.append("| ");
                                sb.append(predicateRules.get(sortHook)
                                                        .apply(sort));
                                sb.append("\n");
                            }
                        });
                    }

                    sb.append(convertFunction(ppk,
                                              ppk.functionRules
                                                 .get(functionLabel)
                                                 .stream()
                                                 .sorted(DefinitionToFunc::sortFunctionRules)
                                                 .collect(toListC()),
                                              functionName,
                                              RuleType.FUNCTION));
                    sb.append("| _ -> raise (Stuck [denormalize (KApply(lbl, (denormalize");
                    sb.append(Integer.toString(arity));
                    sb.append(" c)))])\n");
                    if(constantLabels.contains(functionLabel)) {
                        sb.append(conn.equals("let rec ") ? "and " : conn);
                        sb.append("const");
                        sb.append(encodeStringToAlphanumeric(functionLabel.name()));
                        sb.append(" : k Lazy.t = lazy (");
                        sb.append(encodeStringToFunction(functionLabel));
                        sb.append(" () [Bottom] (-1))\n");
                    }
                    conn = "and ";
                } else if(ppk.anywhereRules.keySet().contains(functionLabel)) {
                    sb.append(conn);
                    String functionName = encodeStringToFunction(functionLabel);
                    sb.append(functionName);
                    int arity = getArity(ppk, functionLabel);
                    sb.append(printFunctionParams(arity));
                    sb.append(" (config: k) (guard: int) : k = let lbl = \n");
                    sb.append(encodeStringToIdentifier(functionLabel));
                    sb.append(" in match c with \n");
                    sb.append(convertFunction(ppk,
                                              ppk.anywhereRules
                                                 .get(functionLabel)
                                                 .stream()
                                                 .collect(toListC()),
                                              functionName,
                                              RuleType.ANYWHERE));
                    sb.append("| ");
                    for(int i = 0; i < arity; i++) {
                        sb.append("k");
                        sb.append(Integer.toString(i));
                        if(i != arity - 1) {
                            sb.append(",");
                        }
                    }
                    sb.append(" -> [KApply");
                    sb.append(Integer.toString(arity));
                    sb.append("(lbl, ");
                    for(int i = 0; i < arity; i++) {
                        sb.append("k");
                        sb.append(Integer.toString(i));
                        if(i != arity - 1) {
                            sb.append(",");
                        }
                    }
                    sb.append(")]\n");
                    conn = "and ";
                } else if(functionLabel.name().isEmpty()) {
                    //placeholder for eval function;
                    sb.append(conn);
                    sb.append("eval (c: normal_kitem) (config: k) : k = match c with KApply(lbl, kl) -> (match lbl with \n");
                    for(KLabel label : Sets.union(ppk.functionSet,
                                                  ppk.anywhereRules.keySet())) {
                        sb.append("|");
                        sb.append(encodeStringToIdentifier(label));
                        sb.append(" -> ");
                        sb.append(encodeStringToFunction(label));
                        int arity = getArity(ppk, label);
                        sb.append(" (normalize");
                        sb.append(Integer.toString(arity));
                        sb.append(" kl) config (-1)\n");
                    }
                    sb.append("| _ -> [denormalize c])\n");
                    sb.append("| _ -> [denormalize c]\n");
                }
            }
        }
        sb.append("let rec lookups_step (c: k) (config: k) (guard: int) : k = match c with \n");

        Comparator<Rule> sorter = (r1, r2) ->
            PredicateChain.start(r1, r2)
                          .comparePredT(r -> r.att().contains("structural"))
                          .comparePredF(r -> r.att().contains("owise"))
                          .comparePredF(r -> indexesPoorly(ppk, r))
                          .result();

        List<Rule> sortedRules =
            ppk.rules
               .stream()
               .sorted(sorter)
               .filter(r -> ! ppk.functionRules.values().contains(r))
               .filter(r -> ! r.att().contains(Attribute.MACRO_KEY))
               .filter(r -> ! r.att().contains(Attribute.ANYWHERE_KEY))
               .collect(toListC());
        Map<Boolean, List<Rule>> groupedByLookup =
            sortedRules.stream()
                       .collect(groupingByC(DefinitionToFunc::hasLookups));

        Pair<Integer, SyntaxBuilder> pair = convert(ppk,
                                                    groupedByLookup.get(true),
                                                    "lookups_step",
                                                    RuleType.REGULAR,
                                                    0);
        int ruleNum = pair.getLeft();
        sb.append(pair.getRight());
        sb.append("| _ -> raise (Stuck c)\n");
        sb.append("let step (c: k) : k = let config = c in match c with \n");
        if(groupedByLookup.containsKey(false)) {
            for(Rule r : groupedByLookup.get(false)) {
                Pair<Integer, SyntaxBuilder> pair2 = convert(ppk, r,
                                                             RuleType.REGULAR,
                                                             ruleNum);
                ruleNum = pair2.getLeft();
                sb.append(pair2.getRight());
            }
        }
        sb.append("| _ -> lookups_step c c (-1)\n");
        sb.append(OCamlIncludes.postludeSB);
        return sb;
    }

    private static <V> Set<V> ancestors(Collection<? extends V> startNodes,
                                        DirectedGraph<V, ?> graph) {
        Queue<V> queue = new LinkedList<V>();
        Set<V> visited = new LinkedHashSet<V>();
        queue.addAll(startNodes);
        visited.addAll(startNodes);
        while(!queue.isEmpty()) {
            V v = queue.poll();
            Collection<V> neighbors = graph.getPredecessors(v);
            for(V n : neighbors) {
                if(!visited.contains(n)) {
                    queue.offer(n);
                    visited.add(n);
                }
            }
        }
        return visited;
    }

    public static SyntaxBuilder runtimeFunction(PreprocessedKORE ppk,
                                                String name,
                                                Rule r) {
        return convertFunction(ppk,
                               singletonList(ppk.convertRuntime(r)),
                               name,
                               RuleType.PATTERN);
    }

    private static int getArity(PreprocessedKORE ppk,
                                KLabel functionLabel) {
        Set<Integer> arities = ppk.productionsFor
                                  .get(functionLabel)
                                  .stream()
                                  .map(Production::arity)
                                  .collect(toSetC());
        if(arities.size() > 1) {
            throw kemCompilerErrorF("KLabel %s has multiple productions with " +
                                    "differing arities: %s",
                                    functionLabel,
                                    ppk.productionsFor.get(functionLabel));
        }
        assert arities.size() == 1;
        return arities.iterator().next();
    }

    private SyntaxBuilder printFunctionParams(long arity) {
        SyntaxBuilder sb = newsb();
        if(arity == 0) {
            sb.append(" (c: unit)");
        } else {
            sb.append(" (c: ");
            String conn2 = "";
            for (int i = 0; i < arity; i++) {
                sb.append(conn2);
                sb.append("k");
                conn2 = " * ";
            }
            sb.append(")");
        }
        return sb;
    }

    private static SyntaxBuilder convertFunction(PreprocessedKORE ppk,
                                                 List<Rule> rules,
                                                 String functionName,
                                                 RuleType type) {
        SyntaxBuilder sb = newsb();
        int ruleNum = 0;
        for(Rule r : rules) {
            Pair<Integer, SyntaxBuilder> pair;
            if(hasLookups(r)) {
                pair = convert(ppk, singletonList(r),
                               functionName, type, ruleNum);
            } else {
                pair = convert(ppk, r, type, ruleNum);
            }
            ruleNum = pair.getLeft();
            sb.append(pair.getRight());
        }
        return sb;
    }

    private static boolean hasLookups(Rule r) {
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

    private static int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"),
                               a2.att().contains("owise"));
    }

    // FIXME(remy): Move to PreprocessedKORE
    private static final class FunctionOrder {
        private final SetMultimap<KLabel, Rule> functionRules;
        private final Map<KLabel, Att> attributesFor;
        private final Map<Sort, KLabel> freshFunctionFor;
        private final Set<KLabel> faLabels;

        private final Map<KLabel, Boolean> canTakeSteps;
        private final BiMap<KLabel, Integer> mapping;
        private final List<Pair<KLabel, KLabel>> traversal;
        private final List<Integer>[] predecessors;

        private final DirectedGraph<KLabel, Object> dependencies;
        private final List<List<KLabel>> order;


        public static FunctionOrder generateOrder(PreprocessedKORE ppk) {
            return new FunctionOrder(ppk);
        }

        public DirectedGraph<KLabel, Object> getDependencies() {
            return dependencies;
        }

        public List<List<KLabel>> getOrder() {
            return order;
        }

        private FunctionOrder(PreprocessedKORE ppk) {
            this.functionRules    = ppk.functionRules;
            this.attributesFor    = ppk.attributesFor;
            this.freshFunctionFor = ppk.freshFunctionFor;
            this.faLabels         = Sets.union(ppk.functionSet,
                                               ppk.anywhereRules.keySet());

            this.canTakeSteps     = initCanTakeSteps();
            this.mapping          = initMapping();
            this.traversal        = initTraversal();
            this.predecessors     = initPredecessors();

            this.dependencies     = initDependencies();
            this.order            = initOrder();
        }

        private Map<KLabel, Boolean> initCanTakeSteps() {
            Map<KLabel, Boolean> res = newHashMap();
            for(KLabel kl : faLabels) {
                res.put(kl, attributesFor.get(kl).contains("canTakeSteps"));
            }
            return res;
        }

        private BiMap<KLabel, Integer> initMapping() {
            BiMap<KLabel, Integer> res = HashBiMap.create();
            int counter = 0;
            for(KLabel lbl : faLabels) { res.put(lbl, counter++); }
            // use blank klabel to simulate dependencies on eval
            res.put(KLabel(""), counter++);
            return res;
        }

        private List<Pair<KLabel, KLabel>> initTraversal() {
            List<Pair<KLabel, KLabel>> res = newArrayList();
            functionRules
                .entries()
                .stream()
                .map(e -> traverse(e.getKey(),
                                   e.getValue().body(),
                                   e.getValue().requires()))
                .map(res::addAll);
            return res;
        }

        private List<Integer>[] initPredecessors() {
            List<Integer>[] res = new List[faLabels.size() + 1];
            for(List<Integer> l : res) { l = newArrayList(); }

            traversal
                .stream()
                .map(p -> Pair.of(mapping.get(p.getLeft()),
                                  mapping.get(p.getRight())))
                .forEach(p -> res[p.getLeft()].add(p.getRight()));

            int dummy = mapping.get(KLabel(""));
            Set<KLabel> ks = canTakeSteps.keySet();

            ks.stream()
              .filter(canTakeSteps::get)
              .map(mapping::get)
              .forEach(i -> res[i].add(dummy));

            ks.stream()
              .map(mapping::get)
              .forEach(i -> res[dummy].add(i));

            return res;
        }

        private DirectedGraph<KLabel, Object> initDependencies() {
            DirectedGraph<KLabel, Object> res = new DirectedSparseGraph<>();

            traversal.stream().forEach(p -> res.addEdge(new Object(),
                                                        p.getLeft(),
                                                        p.getRight()));

            KLabel dummy = KLabel("");
            Set<KLabel> ks = canTakeSteps.keySet();

            ks.stream()
              .filter(canTakeSteps::get)
              .forEach(kl -> res.addEdge(new Object(), kl, dummy));

            ks.stream().forEach(kl -> res.addEdge(new Object(), dummy, kl));

            return res;
        }

        private List<List<KLabel>> initOrder() {
            BiMap<Integer, KLabel> inv = mapping.inverse();
            return mapCollL(c -> mapCollL(inv::get, c),
                            new SCCTarjan().scc(predecessors));
        }

        private List<Pair<KLabel, KLabel>> traverse(KLabel kl,
                                                    K... ks) {
            TraverseKORE tk = new TraverseKORE(kl);
            for(K k : ks) { tk.apply(k); }
            return tk.getAccum();
        }

        private class TraverseKORE extends VisitKORE {
            private final KLabel current;
            private final String hook;
            private final List<Pair<KLabel, KLabel>> accum;

            public TraverseKORE(KLabel current) {
                this.current = current;
                this.hook    = attributesFor
                              .get(current)
                              .<String>getOptional(Attribute.HOOK_KEY)
                              .orElse(".");
                this.accum   = newArrayList();
            }

            public List<Pair<KLabel, KLabel>> getAccum() {
                return accum;
            }

            @Override
            public Void apply(KApply k) {
                if(faLabels.contains(k.klabel())) {
                    accum.add(Pair.of(current, k.klabel()));
                    if("KREFLECTION.fresh".equals(hook)) {
                        for(KLabel fresh : freshFunctionFor.values()) {
                            accum.add(Pair.of(current, fresh));
                        }
                    }
                }

                if(k.klabel() instanceof KVariable) {
                    // this function requires a call to eval,
                    // so we need to add the dummy dependency
                    accum.add(Pair.of(current, KLabel("")));
                }

                return super.apply(k);
            }
        }
    }

    private static enum RuleType {
        FUNCTION, ANYWHERE, REGULAR, PATTERN
    }

    private static <A,B,C,D> Tuple4<A,B,C,D> entryTuple4(Map.Entry<Triple<A,B,C>,D> e) {
        Triple<A,B,C> k = e.getKey();
        return Tuple4.apply(k.getLeft(),
                            k.getMiddle(),
                            k.getRight(),
                            e.getValue());
    }

    // UNCHECKED
    private static Pair<Integer, SyntaxBuilder> convert(PreprocessedKORE ppk,
                                                        List<Rule> rules,
                                                        String functionName,
                                                        RuleType ruleType,
                                                        int ruleNum) {
        SyntaxBuilder sb = newsb();
        int rn = ruleNum;

        NormalizeVariables t = new NormalizeVariables();

        Function<Rule, AttCompare> groupingFunc = r -> {
            return new AttCompare(t.normalize(RewriteToTop.toLeft(r.body())),
                                  "sort");
        };

        Map<AttCompare, List<Rule>>
            grouping = rules.stream().collect(groupingByC(groupingFunc));

        Map<Triple<AttCompare, KLabel, AttCompare>, List<Rule>>
            groupByFirstPrefix = new HashMap<>();

        for(Map.Entry<AttCompare, List<Rule>> entry : grouping.entrySet()) {
            AttCompare left = entry.getKey();

            Function<Rule, Triple<AttCompare, KLabel, AttCompare>> tempRule = r -> {
                KApply lookup = getLookup(r, 0);
                if(lookup == null) { return null; }
                //reconstruct the denormalization for this particular rule
                K left2 = t.normalize(RewriteToTop.toLeft(r.body()));
                K normal = t.normalize(t.applyNormalization(lookup.klist()
                                                                  .items()
                                                                  .get(1),
                                                            left2));
                return Triple.of(left,
                                 lookup.klabel(),
                                 new AttCompare(normal, "sort"));
            };

            groupByFirstPrefix.putAll(entry.getValue()
                                           .stream()
                                           .collect(groupingByC(tempRule)));
        }

        List<Rule> owiseRules = newArrayList();

        // Comparator<Map.Entry<?, List<?>>> sorter = (e1, e2) -> {
        Comparator<Map.Entry<Triple<AttCompare,KLabel,AttCompare>,List<Rule>>> sorter;
        sorter = (e1, e2) -> {
            return Integer.compare(e2.getValue().size(),
                                   e1.getValue().size());
        };

        for(Tuple4<AttCompare, KLabel, AttCompare, List<Rule>> entry2
                : groupByFirstPrefix.entrySet()
                                    .stream()
                                    .sorted(sorter)
                                    .map(DefinitionToFunc::entryTuple4)
                                    .collect(toListC())) {

            K left = entry2._1().get();
            FuncVisitor.VarInfo globalVars = new FuncVisitor.VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk, ruleType, left, globalVars));
            K lookup;
            sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", rn);
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

                List<Lookup> lookups = convertLookups(ppk,
                                                      globalVars,
                                                      r.requires(),
                                                      functionName,
                                                      rn,
                                                      false);

                SBPair pair = convertSideCondition(ppk,
                                                   r.requires(),
                                                   globalVars,
                                                   lookups,
                                                   ! lookups.isEmpty());
                sb.append(pair.getPrefix());
                sb.append(" -> ");
                sb.append(convertRHS(ppk,
                                     ruleType,
                                     RewriteToTop.toRight(r.body()),
                                     globalVars,
                                     rn));
                sb.append(pair.getSuffix());
                sb.addNewline();
                rn++;
            } else {
                KToken dummy = KToken("dummy", Sort("Dummy"));
                KApply kapp  = KApply(entry2._2(), dummy, entry2._3().get());
                List<Lookup> lookupList  = convertLookups(ppk,
                                                          globalVars,
                                                          kapp,
                                                          functionName,
                                                          rn++,
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

                        FuncVisitor.VarInfo vars = new FuncVisitor.VarInfo(globalVars);
                        List<Lookup> lookups = convertLookups(ppk,
                                                              vars,
                                                              r.requires(),
                                                              functionName,
                                                              rn,
                                                              true);
                        sb.append(lookups.get(0).pattern);
                        lookups.remove(0);
                        sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", rn);
                        SBPair pair = convertSideCondition(ppk,
                                                           r.requires(),
                                                           vars,
                                                           lookups,
                                                           lookups.size() > 0);
                        sb.append(pair.getPrefix());
                        sb.append(" -> ");
                        sb.append(convertRHS(ppk,
                                             ruleType,
                                             RewriteToTop.toRight(r.body()),
                                             vars,
                                             rn));
                        sb.append(pair.getSuffix());
                        sb.addNewline();
                        rn++;
                        if(ppk.fastCompilation) {
                            sb.appendf("| _ -> match e with %n");
                        }
                    }
                }
                sb.append(lookupList.get(0).suffix);
                sb.appendf("%n");
            }
        }

        for(Rule r : owiseRules) {
            FuncVisitor.VarInfo globalVars = new FuncVisitor.VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk,
                                 ruleType,
                                 RewriteToTop.toLeft(r.body()),
                                 globalVars));
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.addInteger(rn);
            sb.append(") guards)");

            sb.append(convertComment(r));
            List<Lookup> lookups = convertLookups(ppk,
                                                  globalVars,
                                                  r.requires(),
                                                  functionName,
                                                  rn,
                                                  false);
            SBPair pair = convertSideCondition(ppk,
                                               r.requires(),
                                               globalVars,
                                               lookups,
                                               ! lookups.isEmpty());
            sb.append(" -> ");
            sb.append(convertRHS(ppk,
                                 ruleType,
                                 RewriteToTop.toRight(r.body()),
                                 globalVars,
                                 rn));
            sb.append(pair.getSuffix());
            sb.addNewline();
            rn++;
        }
        return Pair.of(rn, sb);
    }

    // UNCHECKED
    private static boolean indexesPoorly(PreprocessedKORE ppk,
                                         Rule r) {
        Module mm = ppk.mainModule;
        class Holder { boolean b; }
        Holder h = new Holder();
        VisitKORE visitor = new VisitKORE() {
                @Override
                public Void apply(KApply k) {
                    if(   k.klabel().name().equals("<k>")
                       && k.klist().items().size() == 1
                       && k.klist().items().get(0) instanceof KSequence) {
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
                    return super.apply(k);
                }
            };
        visitor.apply(RewriteToTop.toLeft(r.body()));
        visitor.apply(r.requires());
        return h.b;
    }

    // UNCHECKED
    private static KApply getLookup(Rule r, int idx) {
        class Holder {
            int i = 0;
            KApply lookup;
        }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if(h.i > idx) { return null; }
                if(isLookupKLabel(k)) {
                    h.lookup = k;
                    h.i++;
                }
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.lookup;
    }

    // UNCHECKED
    private static Pair<Integer, SyntaxBuilder> convert(PreprocessedKORE ppk,
                                                        Rule r,
                                                        RuleType type,
                                                        int ruleNum) {
        int rn = ruleNum;
        try {
            SyntaxBuilder sb = newsb();
            sb.append(convertComment(r));
            sb.append("| ");
            K left = RewriteToTop.toLeft(r.body());
            K right = RewriteToTop.toRight(r.body());
            K requires = r.requires();
            FuncVisitor.VarInfo vars = new FuncVisitor.VarInfo();
            sb.append(convertLHS(ppk, type, left, vars));
            SyntaxBuilder prefix = FuncVisitor.convert(ppk, vars);
            SyntaxBuilder suffix = newsb();
            if(!requires.equals(KSequence(BooleanUtils.TRUE)) ||
               !"true".equals(prefix.toString())) {
                SBPair pair = convertSideCondition(ppk,
                                                   requires,
                                                   vars,
                                                   Collections.emptyList(),
                                                   true);
                sb.append(pair.getPrefix());
                suffix.append(pair.getSuffix());
            }
            sb.append(" -> ");
            sb.append(convertRHS(ppk, type, right, vars, rn));
            sb.append(suffix);
            sb.addNewline();
            return Pair.of(rn + 1, sb);
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

    private static SyntaxBuilder convertComment(Rule r) {
        if(commentsEnabled) {
            return newsb()
                .addComment(String.format("rule %s requires %s ensures %s | %s",
                                          ToKast.apply(r.body()),
                                          ToKast.apply(r.requires()),
                                          ToKast.apply(r.ensures()),
                                          r.att()));
        } else {
            return newsb();
        }
    }

    // UNCHECKED
    private static SyntaxBuilder convertLHS(PreprocessedKORE ppk,
                                            RuleType type,
                                            K left,
                                            FuncVisitor.VarInfo vars) {
        FuncVisitor visitor = genVisitor(ppk, vars, false, false, false);
        if(type == RuleType.ANYWHERE || type == RuleType.FUNCTION) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            return visitor.apply(kapp.klist().items(), true);
        } else {
            return visitor.apply(left);
        }
    }

    private static SyntaxBuilder convertRHS(PreprocessedKORE ppk,
                                            RuleType type,
                                            K right,
                                            FuncVisitor.VarInfo vars,
                                            int ruleNum) {
        SyntaxBuilder sb = newsb();

        boolean isReg = type == RuleType.REGULAR;
        boolean isPat = type == RuleType.PATTERN;
        boolean isAny = type == RuleType.ANYWHERE;

        if(isPat) {
            for(KVariable var : vars.getVars().keySet()) {
                sb.beginApplication();
                sb.addFunction("Subst.add");
                sb.addArgument(newsbStr(var.name()));
                String fmt = FuncVisitor.isList(var, ppk, vars,
                                                true, false) ? "%s" : "[%s]";
                sb.addArgument(newsbv(fmt(fmt, vars.getVars()
                                                   .get(var)
                                                   .iterator()
                                                   .next())));
            }
            sb.addArgument(newsbv("Subst.empty"));
            for(KVariable ignored : vars.getVars().keySet()) {
                sb.endApplication();
            }
        } else {
            sb.append(genVisitor(ppk, vars, true, false, isAny).apply(right));
        }

        // if(isAny) {
        //     sb = newsb().addMatchSB(sb,
        //                             asList(newsbp("[item]")),
        //                             asList(newsbApp("eval",
        //                                             newsbApp("normalize",
        //                                                      newsbn("item")),
        //                                             newsbn("config"))));
        // }

        if(isReg && ppk.options.getProfileRules()) {
            sb = newsbSeq(newsbApp("print_string",
                                   newsbStr(fmt("succeeded %s rule %d \\n",
                                                type.name().toLowerCase(),
                                                ruleNum))),
                          sb);
        }

        return sb;
    }

    private static SBPair convertSideCondition(PreprocessedKORE ppk,
                                               K requires,
                                               FuncVisitor.VarInfo vars,
                                               List<Lookup> lus,
                                               boolean when) {
        SyntaxBuilder prefix, suffix;
        prefix = newsb();
        for(Lookup lookup : lus) {
            prefix.append(lookup.prefix);
            prefix.append(lookup.pattern);
        }
        SyntaxBuilder temp = FuncVisitor.convert(ppk, vars);
        prefix.addSpace();
        prefix.addKeyword(when ? "when" : "&&");
        prefix.addSpace();
        prefix.append(genVisitor(ppk, vars, true, true, false).apply(requires));
        prefix.addSpace();
        prefix.addKeyword("&&");
        prefix.addSpace();
        prefix.append(temp);
        suffix = newsb(Lists
                       .reverse(lus)
                       .stream()
                       .map(l -> l.suffix)
                       .collect(joiningC()));
        // FIXME(remy): reduce("", String::concat) may be faster than joining()
        return newSBPair(prefix, suffix);
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

    // UNCHECKED
    private static List<Lookup> convertLookups(PreprocessedKORE ppk,
                                               FuncVisitor.VarInfo vars,
                                               K requires,
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
                Optional<Lookup> app = convertLookupsApply(ppk, vars, k,
                                                           ruleNum, h);
                app.ifPresent(results::add);
                return super.apply(k);
            }
        }.apply(requires);
        return results;
    }

    // UNCHECKED
    private static Optional<Lookup> convertLookupsApply(PreprocessedKORE ppk,
                                                        FuncVisitor.VarInfo vars,
                                                        KApply k,
                                                        int rn,
                                                        Holder hold) {
        if(k.klabel().name().equals("#match")) {
            if(k.klist().items().size() != 2) {
                throw kemInternalErrorF(k, "Unexpected arity of lookup: %s",
                                        k.klist().size());
            }
            SyntaxBuilder sb = newsb();
            sb.append(" -> (let e = ");
            sb.append(genVisitor(ppk, vars, true, false, false).apply(k.klist()
                                                                       .items()
                                                                       .get(1)));
            sb.appendf(" in match e with %n");
            sb.append("| [Bottom] -> ");
            sb.append(hold.reapply);
            sb.appendf("%n");
            String prefix = sb.toString();
            sb = newsb();
            sb.append("| ");
            sb.append(genVisitor(ppk, vars, false, false, false).apply(k.klist()
                                                                        .items()
                                                                        .get(0)));
            String pattern = sb.toString();
            String suffix = "| _ -> " + hold.reapply + ")";
            hold.first = false;
            return Optional.of(new Lookup(prefix, pattern, suffix));
        } else if(k.klabel().name().equals("#setChoice")) {
            return convertLookupsChoose(k, ppk, vars, hold, rn, "Set", "e");
        } else if(k.klabel().name().equals("#mapChoice")) {
            return convertLookupsChoose(k, ppk, vars, hold, rn, "Map", "e v");
        }
        return Optional.empty();
    }

    // UNCHECKED
    private static Optional<Lookup> convertLookupsChoose(KApply k,
                                                         PreprocessedKORE ppk,
                                                         FuncVisitor.VarInfo vars,
                                                         Holder hold,
                                                         int rn,
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
        sb.append(genVisitor(ppk, vars, true, false, false).apply(k.klist()
                                                                  .items()
                                                                  .get(1)));
        sb.appendf(" with %n");
        sb.append(choiceString);
        if(hold.first) {
            sb.append("let rec stepElt = fun guards -> ");
        }
        sb.append("if (compare result [Bottom]) = 0 then (match e with ");
        String prefix = sb.toString();
        sb = newsb();
        String suffix2 = String.format("| _ -> [Bottom]) else result%s) collection [Bottom]) in if (compare choice [Bottom]) = 0 then %s else choice",
                                       (hold.first ? " in stepElt Guard.empty" : ""),
                                       hold.reapply);
        String suffix = String.format("%s| _ -> %s)", suffix2, hold.reapply);
        if(hold.first) {
            hold.reapply = String.format("(stepElt (Guard.add (GuardElt.Guard %d) guards))", rn);
        } else {
            hold.reapply = "[Bottom]";
        }
        sb.append("| ");
        sb.append(genVisitor(ppk, vars, false, false, false).apply(k.klist()
                                                                   .items()
                                                                   .get(0)));
        String pattern = sb.toString();
        hold.first = false;
        return Optional.of(new Lookup(prefix, pattern, suffix));
    }

    /**
     * Create a Visitor based on the given information
     */
    private static FuncVisitor genVisitor(PreprocessedKORE ppk,
                                          FuncVisitor.VarInfo vars,
                                          boolean rhs,
                                          boolean useNativeBool,
                                          boolean anywhereRule) {
        return new FuncVisitor(ppk, vars, rhs, useNativeBool, anywhereRule);
    }

    /**
     * Does the given KApply have a lookup-related label?
     * @param k     The KApply to test
     * @return      Whether or not the given KApply has a lookup-related label
     */
    private static boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match")
            || k.klabel().name().equals("#mapChoice")
            || k.klabel().name().equals("#setChoice");
    }

    private static SBPair newSBPair(SyntaxBuilder pre,
                                    SyntaxBuilder suff) {
        return new SBPair(pre, suff);
    }

    private static class SBPair {
        private final SyntaxBuilder prefix, suffix;

        public SBPair(SyntaxBuilder prefix, SyntaxBuilder suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }


        public SyntaxBuilder getPrefix() {
            return prefix;
        }

        public SyntaxBuilder getSuffix() {
            return suffix;
        }
    }
}
