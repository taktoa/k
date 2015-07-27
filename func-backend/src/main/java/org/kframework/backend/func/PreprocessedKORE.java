// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Sentence;
import org.kframework.definition.Definition;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.DeconstructIntegerLiterals;
import org.kframework.kore.compile.GenerateSortPredicateRules;
import org.kframework.kore.compile.LiftToKSequence;
import org.kframework.kore.compile.SimplifyConditions;
import org.kframework.backend.java.kore.compile.ExpandMacros;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import scala.Function1;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Lists;
import org.kframework.definition.Production;
import org.kframework.utils.algorithms.SCCTarjan;

import java.io.Serializable;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.attributes.Att;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KSequence;
import org.kframework.kore.Sort;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

// TODO(remy):
//   - Switch over to Guava collection types
//   - Increase use of immutable data structures and stream processing
//   - Abstract out common patterns in initialization functions
//   - Improve naming of variables etc.
//   - Switch over to getters
//   - Remove unused public fields

/**
 * Preprocesses the incoming KORE
 * <br>
 * The idea here is to make the information coming in
 * from KORE as minimal and as contained as possible,
 * so it will be easier to understand.
 *
 * @author Remy Goldschmidt
 */
public final class PreprocessedKORE implements Serializable {
    /** Set of KLabels that are functions */
    public Set<KLabel> functionSet;
    /** Set of KLabels that are subject to [anywhere] rules */
    public Set<KLabel> anywhereSet;
    /** Multimap from function KLabels to the rules that contain them */
    public SetMultimap<KLabel, Rule> functionRules;
    /** Multimap from anywhere KLabels to the rules that contain them */
    public SetMultimap<KLabel, Rule> anywhereRules;
    /** Map from function KLabels to a list of rules that contain them, ordered on [owise] */
    public Map<KLabel, List<Rule>> functionRulesOrdered;
    /** The topological sort of function calls */
    public List<List<KLabel>> functionOrder;

    /** The set of all defined sorts */
    public Set<Sort> definedSorts;
    /** The set of all defined KLabels */
    public Set<KLabel> definedKLabels;

    /**
     * For a given attribute string, return a map from KLabels
     * to the contents of the attribute KApply.
     * For example, if we have:
     * {@code syntax Bool ::= "isA" KItem [predicate(A)]}
     * {@code syntax Bool ::= "isB" KItem [predicate(B)]}
     * {@code syntax Int ::= Int "+Int" Int [hook(#INT:_+Int_)]}
     * then we have:
     * <pre>
     * "predicate"
     *     KLabel("isA") -&gt; "A"
     *     KLabel("isB") -&gt; "B"
     * "hook"
     *     KLabel("_+Int_") -&gt; "#INT:_+Int_"
     * </pre>
     * Currently, the only attributes recorded are "hook" and "predicate"
     */
    public Map<String, Map<KLabel, String>> attrLabels;

    /**
     * For a given attribute string, return a map from Sorts
     * to the contents of the attribute KApply.
     * For example, if we have:
     * {@code syntax Int ::= #Int [hook(#INT)]}
     * then we have:
     * <pre>
     * "hook"
     *     Sort("Int") -&gt; "#INT"
     * </pre>
     * Currently, the only attribute recorded is "hook"
     */
    public Map<String, Map<Sort, String>> attrSorts;

    /** The same as mainModule.collectionFor, but converted to use Java collections */
    public Map<KLabel, KLabel> collectionFor;

    /** The same as mainModule.productionsFor, but converted to use Java collections */
    public Map<KLabel, Set<Production>> productionsFor;

    /** The same as mainModule.freshFunctionFor, but converted to use Java collections */
    public Map<Sort, KLabel> freshFunctionFor;

    /** A map from rules to a cleaned-up subset of their attributes */
    public Map<Rule, Set<String>> indexedRules;


    private Set<String> initialized;

    private final ExpandMacros expandMacrosObj;

    private final Module     executionModule;
    private final Definition kompiledDefinition;

    private final Module mainModule;
    private final Set<Rule> rules;
    private final Map<Sort, Att> sortAttributesFor;
    private final Map<KLabel, Att> attributesFor;


    private static final String convertLookupsStr
        = "Convert data structures to lookups";
    private static final String generatePredicatesStr
        = "Generate predicates";
    private static final String liftToKSequenceStr
        = "Lift K into KSequence";
    private static final String deconstructIntsStr
        = "Remove matches on integer literals in left hand side";
    private static final String expandMacrosStr
        = "Expand macros rules";
    private static final String simplifyConditionsStr
        = "Simplify side conditions";

    /**
     * Constructor for PreprocessedKORE
     */
    public PreprocessedKORE(CompiledDefinition def,
                            KExceptionManager kem,
                            FileUtil files,
                            GlobalOptions globalOptions,
                            KompileOptions kompileOptions) {

        executionModule       = def.executionModule();

        kompiledDefinition    = def.kompiledDefinition;

        expandMacrosObj       = new ExpandMacros(executionModule,
                                                 kem,
                                                 files,
                                                 globalOptions,
                                                 kompileOptions);

        initialized = Sets.newHashSet();

        mainModule = mainModulePipeline(executionModule);

        definedSorts      = stream(mainModule.definedSorts()).collect(Collectors.toSet());
        definedKLabels    = stream(mainModule.definedKLabels()).collect(Collectors.toSet());
        rules             = stream(mainModule.rules()).collect(Collectors.toSet());
        attributesFor     = scalaMapAsJava(mainModule.attributesFor());
        sortAttributesFor = scalaMapAsJava(mainModule.sortAttributesFor());
        collectionFor     = scalaMapAsJava(mainModule.collectionFor());
        freshFunctionFor  = scalaMapAsJava(mainModule.freshFunctionFor());

        initializeFinal();
    }

    /**
     * Process incoming K at runtime
     */
    public K runtimeProcess(K k) {
        return new LiftToKSequence().convert(expandMacrosObj.expand(k));
    }

    // Returns a transformation pipeline for the main module
    private Module mainModulePipeline(Module input) {
        BiFunction<UnaryOperator<Sentence>, String, ModuleTransformer> fromST;
        fromST = (conv, str) -> {
            return ModuleTransformer.fromSentenceTransformer(conv, str);
        };

        ConvertDataStructureToLookup convertLookupsObj;
        GenerateSortPredicateRules   generatePredicatesObj;
        LiftToKSequence              liftToKSequenceObj;
        DeconstructIntegerLiterals   deconstructIntsObj;
        SimplifyConditions           simplifyConditionsObj;

        convertLookupsObj     = new ConvertDataStructureToLookup(executionModule);
        generatePredicatesObj = new GenerateSortPredicateRules(kompiledDefinition);
        liftToKSequenceObj    = new LiftToKSequence();
        deconstructIntsObj    = new DeconstructIntegerLiterals();
        simplifyConditionsObj = new SimplifyConditions();

        ModuleTransformer
            convertLookupsMT, liftToKSequenceMT,
            simplifyConditionsMT, deconstructIntsMT,
            expandMacrosMT;

        Function1<Module, Module> generatePredicatesMT;

        convertLookupsMT     = fromST.apply(convertLookupsObj::convert,
                                            convertLookupsStr);
        liftToKSequenceMT    = fromST.apply(liftToKSequenceObj::convert,
                                            liftToKSequenceStr);
        simplifyConditionsMT = fromST.apply(simplifyConditionsObj::convert,
                                            simplifyConditionsStr);
        deconstructIntsMT    = fromST.apply(deconstructIntsObj::convert,
                                            deconstructIntsStr);
        expandMacrosMT       = fromST.apply(expandMacrosObj::expand,
                                            expandMacrosStr);

        generatePredicatesMT = func(generatePredicatesObj::gen);

        return deconstructIntsMT
            .andThen(convertLookupsMT)
            .andThen(expandMacrosMT)
            .andThen(generatePredicatesMT)
            .andThen(liftToKSequenceMT)
            .andThen(simplifyConditionsMT)
            .apply(input);
    }

    // Runs all of the final initialization functions
    private void initializeFinal() {
        initializeProductionsFor();
        initializeFunctionRules();
        initializeAnywhereRules();
        initializeFunctionSet();
        initializeAnywhereSet();
        initializeFunctionRulesOrdered();
        initializeFunctionOrder();
        initializeAttrLabels();
        initializeAttrSorts();
        initializeIndexedRules();
    }

    // Initializes attrLabels
    // TODO(remy): this should be generalized to all label attributes
    private void initializeAttrLabels() {
        if(initialized.contains("attrLabels")) { return; }

        attrLabels = Maps.newHashMap();
        Map<KLabel, String> hm = Maps.newHashMap();
        Map<KLabel, String> pm = Maps.newHashMap();

        for(Map.Entry<KLabel, Att> e : attributesFor.entrySet()) {
            String h = e.getValue().<String>getOptional(Attribute.HOOK_KEY).orElse("");
            String p = e.getValue().<String>getOptional(Attribute.PREDICATE_KEY).orElse("");
            if(! "".equals(h)) {
                hm.put(e.getKey(), h);
            }
            if(! "".equals(p)) {
                pm.put(e.getKey(), p);
            }
        }

        attrLabels.put(Attribute.HOOK_KEY, hm);
        attrLabels.put(Attribute.PREDICATE_KEY, pm);

        initialized.add("attrLabels");
    }

    // Initializes attrSorts
    // TODO(remy): this should be generalized to all sort attributes
    private void initializeAttrSorts() {
        if(initialized.contains("attrSorts")) { return; }

        attrSorts = Maps.newHashMap();
        Map<Sort, String> hm = Maps.newHashMap();
        Map<Sort, String> pm = Maps.newHashMap();

        for(Map.Entry<Sort, Att> e : sortAttributesFor.entrySet()) {
            String h = e.getValue().<String>getOptional(Attribute.HOOK_KEY).orElse("");
            String p = e.getValue().<String>getOptional(Attribute.PREDICATE_KEY).orElse("");
            if(! "".equals(h)) {
                hm.put(e.getKey(), h);
            }
            if(! "".equals(p)) {
                pm.put(e.getKey(), p);
            }
        }

        attrSorts.put(Attribute.HOOK_KEY, hm);
        attrSorts.put(Attribute.PREDICATE_KEY, pm);

        initialized.add("attrSorts");
    }

    // Initializes productionsFor
    private void initializeProductionsFor() {
        if(initialized.contains("productionsFor")) { return; }

        productionsFor = Maps.newHashMap();

        Map<KLabel, scala.collection.immutable.Set<Production>> tempPF;
        tempPF = scalaMapAsJava(mainModule.productionsFor());

        for(KLabel k : tempPF.keySet()) {
            productionsFor.put(k, stream(tempPF.get(k)).collect(Collectors.toSet()));
        }

        initialized.add("productionsFor");
    }

    // Initializes functionRules
    private void initializeFunctionRules() {
        if(initialized.contains("functionRules")) { return; }

        functionRules = HashMultimap.create();

        for(Rule r : rules) {
            Optional<KLabel> mkl = getKLabelIfFunctionRule(r);
            if(mkl.isPresent()) {
                functionRules.put(mkl.get(), r);
            }
        }

        initialized.add("functionRules");
    }

    // Initializes anywhereRules
    private void initializeAnywhereRules() {
        if(initialized.contains("anywhereRules")) { return; }

        anywhereRules = HashMultimap.create();

        for(Rule r : rules) {
            Optional<KLabel> mkl = getKLabelIfAnywhereRule(r);
            if(mkl.isPresent()) {
                anywhereRules.put(mkl.get(), r);
            }
        }

        initialized.add("anywhereRules");
    }

    // Initializes functionRulesOrdered
    private void initializeFunctionRulesOrdered() {
        initializeFunctionRules();
        if(initialized.contains("functionRulesOrdered")) { return; }

        functionRulesOrdered = Maps.newHashMap();

        for(KLabel k : functionRules.keySet()) {
            functionRulesOrdered.put(k, lookupSortedFunctionRule(k));
        }

        initialized.add("functionRulesOrdered");
    }


    // Initializes functionSet
    private void initializeFunctionSet() {
        initializeFunctionRules();
        if(initialized.contains("functionSet")) { return; }

        functionSet = Sets.newHashSet(functionRules.keySet());

        for(Production p : iterable(mainModule.productions())) {
            if(p.att().contains(Attribute.FUNCTION_KEY)) {
                functionSet.add(p.klabel().get());
            }
        }

        initialized.add("functionSet");
    }

    // Initializes anywhereSet
    private void initializeAnywhereSet() {
        initializeAnywhereRules();
        if(initialized.contains("anywhereSet")) { return; }

        anywhereSet = Sets.newHashSet(anywhereRules.keySet());

        for(Production p : iterable(mainModule.productions())) {
            if(p.att().contains("anywhere")) {
                anywhereSet.add(p.klabel().get());
            }
        }

        initialized.add("anywhereSet");
    }

    // Initialize functionOrder to a topological sort of the
    // function rule dependency graph.
    // TODO(remy): maybe remove given that it is not in dwightguth/ocaml8
    private void initializeFunctionOrder() {
        initializeFunctionRules();
        initializeFunctionSet();
        if(initialized.contains("functionOrder")) { return; }

        BiMap<KLabel, Integer> mapping = HashBiMap.create();

        int counter = 0;

        for(KLabel lbl : functionSet) {
            mapping.put(lbl, counter++);
        }

        List<Integer>[] predecessors = new List[functionSet.size()];

        for(int i = 0; i < predecessors.length; i++) {
            predecessors[i] = Lists.newArrayList();
        }

        class GetPredecessors extends VisitKORE {
            private final KLabel current;

            public GetPredecessors(KLabel current) {
                this.current = current;
            }

            @Override
            public Void apply(KApply k) {
                if(functionSet.contains(k.klabel())) {
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

        functionOrder = components
                       .stream()
                       .map(l -> l.stream()
                                  .map(i -> mapping.inverse().get(i))
                                  .collect(Collectors.toList()))
                       .collect(Collectors.toList());
    }

    // Initializes indexedRules
    private void initializeIndexedRules() {
        if(initialized.contains("indexedRules")) { return; }

        indexedRules = Maps.newHashMap();
        Set<Rule> funcRules = Sets.newHashSet();

        for(Map.Entry<KLabel, Rule> e : functionRules.entries()) {
            funcRules.add(e.getValue());
        }

        for(Rule r : rules) {
            Set<String> tmp = Sets.newHashSet();

            if(isMacroRule(r))        { tmp.add("macro"); }
            if(isLookupRule(r))       { tmp.add("lookup"); }
            if(funcRules.contains(r)) { tmp.add("function"); }

            indexedRules.put(r, tmp);
        }

        initialized.add("indexedRules");
    }

    // Helper function for initializeFunctionRulesOrdered
    private List<Rule> lookupSortedFunctionRule(KLabel kl) {
        return functionRules.get(kl)
                            .stream()
                            .sorted(PreprocessedKORE::sortFunctionRules)
                            .collect(Collectors.toList());
    }

    // Helper function for initializeFunctionRules
    private Optional<KLabel> getKLabelIfFunctionRule(Rule r) {
        return getKLabelIfPredicate(r, x -> x.contains(Attribute.FUNCTION_KEY));
    }

    // Helper function for initializeFunctionRules
    private Optional<KLabel> getKLabelIfAnywhereRule(Rule r) {
        return getKLabelIfPredicate(r, x -> r.att().contains("anywhere"));
    }

    // Return the KLabel of a rule if it meets a predicate
    private Optional<KLabel> getKLabelIfPredicate(Rule r, Predicate<Att> pred) {
        K left = RewriteToTop.toLeft(r.body());
        boolean is = false;
        KSequence kseq;
        KApply kapp = null;

        if(left instanceof KSequence) {
            kseq = (KSequence) left;
            if(kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                kapp = (KApply) kseq.items().get(0);
                is = pred.test(mainModule.attributesFor().apply(kapp.klabel()));
            }
        }

        return is ? Optional.ofNullable(kapp.klabel()) : Optional.empty();
    }

    // Sort comparator on function rules, where [owise] rules go last
    private static int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"), a2.att().contains("owise"));
    }

    // Check a rule for map/set lookups
    public boolean isLookupRule(Rule r) {
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

    // Check whether a rule is a macro
    private boolean isMacroRule(Rule r) {
        return r.att().contains("macro");
    }

    // Check whether a given KLabel refers to a map/set lookup
    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match")
            || k.klabel().name().equals("#mapChoice")
            || k.klabel().name().equals("#setChoice");
    }

    /**
     * Two PreprocessedKOREs are equal if their underlying mainModules are equal
     */
    @Override
    public boolean equals(Object o) {
        if(o instanceof PreprocessedKORE) {
            return mainModule.equals(((PreprocessedKORE) o).mainModule);
        }
        return false;
    }

    /**
     * The hashCode of a PreprocessedKORE is that of its mainModule
     */
    @Override
    public int hashCode() {
        return mainModule.hashCode();
    }

    /**
     * For toString(), just print out the mainModule
     */
    @Override
    public String toString() {
        return mainModule.toString();
    }
}
