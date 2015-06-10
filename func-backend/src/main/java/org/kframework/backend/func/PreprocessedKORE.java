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
    public final SetMultimap<KLabel, Rule> functionRules;
    public final List<List<KLabel>> functionOrder;
    public final Set<Sort> definedSorts;
    public final Set<KLabel> definedKLabels;
    public final Map<String, Map<KLabel, String>> attrLabels;
    public final Map<String, Map<Sort, String>> attrSorts;
    public final Map<KLabel, KLabel> collectionFor;
    public final Set<Rule> nonLookupRules;
    public final Set<Rule> hasLookupRules;

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

    private static final String convertLookupsStr     = "Convert data structures to lookups";
    private static final String generatePredicatesStr = "Generate predicates";
    private static final String liftToKSequenceStr    = "Lift K into KSequence";
    private static final String deconstructIntsStr    = "Remove matches on integer literals in left hand side";
    private static final String expandMacrosStr       = "Expand macros rules";
    private static final String simplifyConditionsStr = "Simplify side conditions";

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

        functionRules = getFunctionRules();
        functionSet   = getFunctionSet(functionRules);
        functionOrder = getFunctionOrder(functionSet, functionRules);
        attrLabels    = getAttrLabels(attributesFor);
        attrSorts     = getAttrSorts(sortAttributesFor);

        Tuple2<Set<Rule>, Set<Rule>> lr = partitionLookupRules(rules);
        hasLookupRules = lr._1();
        nonLookupRules = lr._2();
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

    public K runtimeProcess(K k) {
        return liftToKSequenceObj.convert(expandMacrosObj.expand(k));
    }

    private boolean attPairPrint(StringBuilder sb, boolean isFirst, KApply ka) {
        Set<KLabel> bannedKLabels = new HashSet<>();
        bannedKLabels.add(KLabel("productionID"));
        bannedKLabels.add(KLabel("latex"));
        boolean ret = isFirst;
        if(! bannedKLabels.contains(ka.klabel())) {
            if(! ret) {
                sb.append(", ");
            }
            sb.append(ka.klabel());
            sb.append("(");
            List<String> res = new ArrayList<>();
            for(K kli : ka.klist().items()) {
                if(kli instanceof KApply) {
                    KApply klia = (KApply) kli;
                    if("#token".equals(klia.klabel())) {
                        res.add(klia.klist().items().get(0).toString());
                    }
                }
            }
            int i = res.size();
            for(String s : res) {
                i--;
                sb.append(s);
                if(i > 0) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            ret = false;
        }
        return ret;
    }

    private void prettyPrint(StringBuilder sb, String eol, Att a) {
        Set<String> banned = new HashSet<>();
        banned.add("Location");
        banned.add("Source");
        banned.add("org.kframework.attributes.Source");
        banned.add("org.kframework.attributes.Location");
        Map<String, KApply> am = scalaMapAsJava(a.attMap());
        Set<String> keys =  am
                           .keySet()
                           .stream()
                           .filter(x -> ! banned.contains(x))
                           .collect(Collectors.toSet());
        sb.append("[");
        boolean isFirst = true;
        for(String key : keys) {
            isFirst = attPairPrint(sb, isFirst, am.get(key));
        }
        sb.append("]");
    }

    private void prettyPrint(StringBuilder sb, String eol, K k) {
        sb.append((new CST(k)).render());
    }
    
    private void prettyPrint(StringBuilder sb, String eol, Rule r) {        
        sb.append(eol);
        sb.append("Body: ");
        sb.append(eol);
        prettyPrint(sb, eol + "    ", r.body());

        sb.append(eol);
        sb.append("Requires: ");
        sb.append(eol);
        prettyPrint(sb, eol + "    ", r.requires());

        sb.append(eol);
        sb.append("Ensures: ");
        sb.append(eol);
        prettyPrint(sb, eol + "    ", r.ensures());
        
        sb.append(eol);
        sb.append("Attributes: ");
        sb.append(eol);
        prettyPrint(sb, eol + "    ", r.att());
    }

    private void prettyPrint(StringBuilder sb, String eol, Sort s) {
        sb.append(s.toString());
        sb.append(eol);
    }

    private void prettyPrint(StringBuilder sb, String eol) {
        sb.append("Sorts:");
        sb.append(eol);
        for(Sort s : definedSorts) {
            prettyPrint(sb, eol, s);
        }
        sb.append(eol);

        sb.append("KLabels:");
        sb.append(eol);
        for(KLabel l : definedKLabels) {
            sb.append(l.toString());
            sb.append(eol);
        }
        sb.append(eol);

        sb.append("Function Rules:");
        sb.append(eol);
        for(Map.Entry<KLabel, Rule> e : functionRules.entries()) {
            sb.append(e.getKey());
            sb.append(" -> ");
            prettyPrint(sb, eol + "    ", e.getValue());
            sb.append(eol);
        }
        sb.append(eol);

        sb.append("Function Set:");
        sb.append(eol);
        sb.append(functionSet.toString());
        sb.append(eol);
        sb.append(eol);

        sb.append("Function Order:");
        sb.append(eol);
        for(List<KLabel> l : functionOrder) {
            sb.append(l.toString());
            sb.append(eol);
        }
        sb.append(eol);

        sb.append("Rules with Lookups:");
        sb.append(eol);
        for(Rule r : hasLookupRules) {
            prettyPrint(sb, eol, r);
        }
        sb.append(eol);

        sb.append("Rules without Lookups:");
        sb.append(eol);
        for(Rule r : nonLookupRules) {
            prettyPrint(sb, eol, r);
        }
        sb.append(eol);

        sb.append("collectionFor:");
        sb.append(eol);
        for(KLabel key : collectionFor.keySet()) {
            sb.append(key.toString());
            sb.append(" -> ");
            sb.append(collectionFor.get(key).toString());
            sb.append(eol);
        }
        sb.append(eol);

        sb.append("attributesFor:");
        sb.append(eol);
        for(KLabel key : attributesFor.keySet()) {
            sb.append(key.toString());
            sb.append(" -> ");
            prettyPrint(sb, eol, attributesFor.get(key));
            sb.append(eol);
        }
        sb.append(eol);

        sb.append("sortAttributesFor:");
        sb.append(eol);
        for(Sort key : sortAttributesFor.keySet()) {
            sb.append(key.toString());
            sb.append(" -> ");
            prettyPrint(sb, eol, sortAttributesFor.get(key));
            sb.append(eol);
        }
        sb.append(eol);
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        prettyPrint(sb, "\n");
        return sb.toString();
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

    private Set<KLabel> getFunctionSet(SetMultimap<KLabel, Rule> fr) {
        Set<KLabel> fs = new HashSet<>(fr.keySet());

        for(Production p : iterable(mainModule.productions())) {
            if(p.att().contains(Attribute.FUNCTION_KEY)) {
                fs.add(p.klabel().get());
            }
        }

        return fs;
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

    private Tuple2<Set<Rule>, Set<Rule>> partitionLookupRules(Set<Rule> rs) {
        Map<Boolean, Set<Rule>> sr
            = rs.stream().collect(Collectors.groupingBy(this::hasLookups, Collectors.toSet()));
        Set<Rule> yes = sr.get(Boolean.TRUE);
        Set<Rule> no = sr.get(Boolean.FALSE);
        return new Tuple2<Set<Rule>, Set<Rule>>(yes, no);
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

    private boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match") || k.klabel().name().equals("#mapChoice") || k.klabel().name().equals("#setChoice");
    }

    private ModuleTransformer convertLookupsMT() {
        return ModuleTransformer.fromSentenceTransformer(convertLookupsObj::convert, convertLookupsStr);
    }

    // TODO(taktoa): figure out why I can't make this a ModuleTransformer
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
}
