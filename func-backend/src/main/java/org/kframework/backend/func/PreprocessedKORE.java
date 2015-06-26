package org.kframework.backend.func;

import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
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
import scala.Tuple2;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.kframework.definition.Production;
import org.kframework.utils.algorithms.SCCTarjan;

import java.util.function.UnaryOperator;
import java.util.function.Function;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

/**
 * @author: Remy Goldschmidt
 */
public final class PreprocessedKORE {
    public final Set<KLabel> functionSet;
    public final Set<KLabel> anywhereSet;
    public final Map<KLabel, List<Rule>> functionRulesOrdered;
    public final List<List<KLabel>> functionOrder;
    public final Set<Sort> definedSorts;
    public final Set<KLabel> definedKLabels;
    public final Map<String, Map<KLabel, String>> attrLabels;
    public final Map<String, Map<Sort, String>> attrSorts;
    public final Map<KLabel, KLabel> collectionFor;
    public final Map<KLabel, Set<Production>> productionsFor;
    public final Map<Rule, Set<String>> indexedRules;
    public final Map<Sort, KLabel> freshFunctionFor;
    public final SetMultimap<KLabel, Rule> functionRules;

    private final Module mainModule;
    private final Set<Rule> rules;
    private final Map<Sort, Att> sortAttributesFor;
    private final Map<KLabel, Att> attributesFor;

    private final ConvertDataStructureToLookup convertLookupsObj;
    private final GenerateSortPredicateRules   generatePredicatesObj;
    private final LiftToKSequence              liftToKSequenceObj;
    private final DeconstructIntegerLiterals   deconstructIntsObj;
    private final ExpandMacros                 expandMacrosObj;
    private final SimplifyConditions           simplifyConditionsObj;

    private final Definition kompiledDefinition;

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

    public PreprocessedKORE(CompiledDefinition def,
                            KExceptionManager kem,
                            FileUtil files,
                            GlobalOptions globalOptions,
                            KompileOptions kompileOptions) {
        Module executionModule;
        Function1<Module, Module> pipeline;

        executionModule = def.executionModule();
        kompiledDefinition = def.kompiledDefinition;

        convertLookupsObj     = new ConvertDataStructureToLookup(executionModule);
        generatePredicatesObj = new GenerateSortPredicateRules(kompiledDefinition);
        liftToKSequenceObj    = new LiftToKSequence();
        deconstructIntsObj    = new DeconstructIntegerLiterals();
        expandMacrosObj       = new ExpandMacros(executionModule,
                                                 kem,
                                                 files,
                                                 globalOptions,
                                                 kompileOptions);
        simplifyConditionsObj = new SimplifyConditions();

        pipeline =        deconstructIntsMT()
                 .andThen(convertLookupsMT())
                 .andThen(expandMacrosMT())
                 .andThen(generatePredicatesMT())
                 .andThen(liftToKSequenceMT())
                 .andThen(simplifyConditionsMT());

        mainModule = pipeline.apply(executionModule);

        definedSorts      = stream(mainModule.definedSorts()).collect(Collectors.toSet());
        definedKLabels    = stream(mainModule.definedKLabels()).collect(Collectors.toSet());
        rules             = stream(mainModule.rules()).collect(Collectors.toSet());
        attributesFor     = scalaMapAsJava(mainModule.attributesFor());
        sortAttributesFor = scalaMapAsJava(mainModule.sortAttributesFor());
        collectionFor     = scalaMapAsJava(mainModule.collectionFor());
        freshFunctionFor  = scalaMapAsJava(mainModule.freshFunctionFor());
        productionsFor    = getProductionsFor(mainModule);

        functionRules = getFunctionRules();
        functionRulesOrdered = getFunctionRulesOrdered(functionRules);
        functionSet   = getFunctionSet(functionRules);
        anywhereSet   = getAnywhereSet(functionRules);
        functionOrder = getFunctionOrder(functionSet, functionRules);
        attrLabels    = getAttrLabels(attributesFor);
        attrSorts     = getAttrSorts(sortAttributesFor);

        indexedRules = getIndexedRules(rules);
    }

    public K runtimeProcess(K k) {
        return liftToKSequenceObj.convert(expandMacrosObj.expand(k));
    }

    private Map<KLabel, String> getHookLabels(Map<KLabel, Att> af) {
        Map<KLabel, String> res = new HashMap<>();
        for(Map.Entry<KLabel, Att> e : af.entrySet()) {
            String h = e.getValue().<String>getOptional(Attribute.HOOK_KEY).orElse("");
            if(! "".equals(h)) {
                res.put(e.getKey(), h);
            }
        }
        return res;
    }

    private Map<String, Map<KLabel, String>> getAttrLabels(Map<KLabel, Att> af) {
        Map<String, Map<KLabel, String>> res = new HashMap<>();
        Map<KLabel, String> hm = new HashMap<>();
        Map<KLabel, String> pm = new HashMap<>();
        for(Map.Entry<KLabel, Att> e : af.entrySet()) {
            String h = e.getValue().<String>getOptional(Attribute.HOOK_KEY).orElse("");
            String p = e.getValue().<String>getOptional(Attribute.PREDICATE_KEY).orElse("");
            if(! "".equals(h)) {
                hm.put(e.getKey(), h);
            }
            if(! "".equals(p)) {
                pm.put(e.getKey(), p);
            }
        }
        res.put(Attribute.HOOK_KEY, hm);
        res.put(Attribute.PREDICATE_KEY, pm);
        return res;
    }

    private Map<String, Map<Sort, String>> getAttrSorts(Map<Sort, Att> saf) {
        Map<String, Map<Sort, String>> res = new HashMap<>();
        Map<Sort, String> hm = new HashMap<>();
        Map<Sort, String> pm = new HashMap<>();
        for(Map.Entry<Sort, Att> e : saf.entrySet()) {
            String h = e.getValue().<String>getOptional(Attribute.HOOK_KEY).orElse("");
            String p = e.getValue().<String>getOptional(Attribute.PREDICATE_KEY).orElse("");
            if(! "".equals(h)) {
                hm.put(e.getKey(), h);
            }
            if(! "".equals(p)) {
                pm.put(e.getKey(), p);
            }
        }
        res.put(Attribute.HOOK_KEY, hm);
        res.put(Attribute.PREDICATE_KEY, pm);
        return res;
    }

    private Map<KLabel, Set<Production>> getProductionsFor(Module main) {
        Map<KLabel, scala.collection.immutable.Set<Production>> m = scalaMapAsJava(main.productionsFor());
        Map<KLabel, Set<Production>> out = new HashMap<>();
        for(KLabel k : m.keySet()) {
            out.put(k, stream(m.get(k)).collect(Collectors.toSet()));
        }
        return out;
    }

    private Optional<KLabel> getKLabelIfFunctionRule(Rule r) {
        K left = RewriteToTop.toLeft(r.body());
        boolean is = false;
        KSequence kseq;
        KApply kapp = null;

        if(left instanceof KSequence) {
            kseq = (KSequence) left;
            if(kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                kapp = (KApply) kseq.items().get(0);
                is = mainModule.attributesFor().apply(kapp.klabel()).contains(Attribute.FUNCTION_KEY);
            }
        }

        return is ? Optional.ofNullable(kapp.klabel()) : Optional.empty();
    }

    private Optional<KLabel> getKLabelIfAnywhereRule(Rule r) {
        K left = RewriteToTop.toLeft(r.body());
        boolean is = false;
        KSequence kseq;
        KApply kapp = null;

        if(left instanceof KSequence) {
            kseq = (KSequence) left;
            if(kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                kapp = (KApply) kseq.items().get(0);
                is = r.att().contains("anywhere");
            }
        }

        return is ? Optional.ofNullable(kapp.klabel()) : Optional.empty();
    }


    private SetMultimap<KLabel, Rule> getFunctionRules() {
        SetMultimap<KLabel, Rule> fr = HashMultimap.create();

        for(Rule r : rules) {
            Optional<KLabel> mkl = getKLabelIfFunctionRule(r);
            if(mkl.isPresent()) {
                fr.put(mkl.get(), r);
            }
        }

        return fr;
    }

    private Map<KLabel, List<Rule>> getFunctionRulesOrdered(SetMultimap<KLabel, Rule> fr) {
        Map<KLabel, List<Rule>> result = new HashMap<>();
        List<Rule> rl;

        for(KLabel k : fr.keySet()) {
            rl = fr.get(k)
                   .stream()
                   .sorted(this::sortFunctionRules)
                   .collect(Collectors.toList());
            result.put(k, rl);
        }

        return result;
    }

    private int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"), a2.att().contains("owise"));
    }

    private Set<KLabel> getFunctionSet(SetMultimap<KLabel, Rule> fr) {
        Set<KLabel> fs = new HashSet<>(fr.keySet());

        for(Production p : iterable(mainModule.productions())) {
            if(p.att().contains(Attribute.FUNCTION_KEY)) {
                fs.add(p.klabel().get());
            }
        }

        return fs;
    }

    private Set<KLabel> getAnywhereSet(SetMultimap<KLabel, Rule> ar) {
        Set<KLabel> as = new HashSet<>(ar.keySet());

        for(Production p : iterable(mainModule.productions())) {
            if(p.att().contains("anywhere")) {
                as.add(p.klabel().get());
            }
        }

        return as;
    }

    private List<List<KLabel>> getFunctionOrder(Set<KLabel> fs, SetMultimap<KLabel, Rule> fr) {
        BiMap<KLabel, Integer> mapping = HashBiMap.create();

        int counter = 0;

        for(KLabel lbl : fs) {
            mapping.put(lbl, counter++);
        }

        List<Integer>[] predecessors = new List[fs.size()];

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
                if (fs.contains(k.klabel())) {
                    predecessors[mapping.get(current)].add(mapping.get(k.klabel()));
                }
                return super.apply(k);
            }
        }

        for(Map.Entry<KLabel, Rule> entry : fr.entries()) {
            GetPredecessors visitor = new GetPredecessors(entry.getKey());
            visitor.apply(entry.getValue().body());
            visitor.apply(entry.getValue().requires());
        }

        List<List<Integer>> components = new SCCTarjan().scc(predecessors);

        return components
               .stream()
               .map(l -> l.stream()
                          .map(i -> mapping.inverse().get(i))
                          .collect(Collectors.toList()))
               .collect(Collectors.toList());
    }

    private Map<Rule, Set<String>> getIndexedRules(Set<Rule> rs) {
        Map<Rule, Set<String>> ret = new HashMap<>();
        Set<Rule> funcRules = new HashSet<>();
        for(Map.Entry<KLabel, Rule> e : functionRules.entries()) {
            funcRules.add(e.getValue());
        }
        for(Rule r : rs) {
            Set<String> tmp = new HashSet<>();
            if(isMacro(r)) {
                tmp.add("macro");
            }
            if(hasLookups(r)) {
                tmp.add("lookup");
            }
            if(funcRules.contains(r)) {
                tmp.add("function");
            }
            ret.put(r, tmp);
        }
        return ret;
    }

    public Boolean hasLookups(Rule r) {
        class Holder { boolean b; }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                h.b |= isLookupKLabel(k);
                return super.apply(k);
            }
        }.apply(r.requires());
        return Boolean.valueOf(h.b);
    }

    private boolean isMacro(Rule r) {
        return false; // FIXME(remy)
    }

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match")
            || k.klabel().name().equals("#mapChoice")
            || k.klabel().name().equals("#setChoice");
    }

    private ModuleTransformer convertLookupsMT() {
        return ModuleTransformer.fromSentenceTransformer(convertLookupsObj::convert, convertLookupsStr);
    }

    private Function1<Module, Module> generatePredicatesMT() {
        return func(generatePredicatesObj::gen);
    }

    private ModuleTransformer liftToKSequenceMT() {
        return ModuleTransformer.fromSentenceTransformer(liftToKSequenceObj::convert, liftToKSequenceStr);
    }

    private ModuleTransformer simplifyConditionsMT() {
        return ModuleTransformer.fromSentenceTransformer(simplifyConditionsObj::convert, simplifyConditionsStr);
    }

    private ModuleTransformer deconstructIntsMT() {
        return ModuleTransformer.fromSentenceTransformer(deconstructIntsObj::convert, deconstructIntsStr);
    }

    private ModuleTransformer expandMacrosMT() {
        return ModuleTransformer.fromSentenceTransformer(expandMacrosObj::expand, expandMacrosStr);
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof PreprocessedKORE) {
            return mainModule.equals(((PreprocessedKORE) o).mainModule)
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mainModule.hashCode();
    }

    @Override
    public String toString() {
        return mainModule.toString();
    }
}
