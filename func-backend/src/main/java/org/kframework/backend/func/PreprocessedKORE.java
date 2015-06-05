package org.kframework.backend.func;

import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Definition;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.DeconstructIntegerLiterals;
import org.kframework.kore.compile.GenerateSortPredicateRules;
import org.kframework.kore.compile.LiftToKSequence;
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
    public final Module mainModule;
    public final Set<KLabel> functionSet;
    public final SetMultimap<KLabel, Rule> functionRules;
    public final List<List<KLabel>> functionOrder;
    public final Set<Sort> definedSorts;
    public final Set<KLabel> definedKLabels;
    public final Set<Rule> rules;
    public final Map<Sort, Att> sortAttributesFor;
    public final Map<KLabel, Att> attributesFor;
    public final Map<KLabel, KLabel> collectionFor;
    public final Set<Rule> nonLookupRules;
    public final Set<Rule> hasLookupRules;

    private final ConvertDataStructureToLookup convertLookupsObj;
    private final GenerateSortPredicateRules   generatePredicatesObj;
    private final LiftToKSequence              liftToKSequenceObj;
    private final DeconstructIntegerLiterals   deconstructIntsObj;
    private final ExpandMacros                 expandMacrosObj;

    private final Definition kompiledDefinition;

    private static final String convertLookupsStr     = "Convert data structures to lookups";
    private static final String generatePredicatesStr = "Generate predicates";
    private static final String liftToKSequenceStr    = "Lift K into KSequence";
    private static final String deconstructIntsStr    = "Remove matches on integer literals in left hand side";
    private static final String expandMacrosStr       = "Expand macros rules";

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
        
        pipeline =        deconstructIntsMT()
                 .andThen(convertLookupsMT())
                 .andThen(expandMacrosMT())
                 .andThen(generatePredicatesMT())
                 .andThen(liftToKSequenceMT());

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

        Tuple2<Set<Rule>, Set<Rule>> lr = partitionLookupRules(rules);
        hasLookupRules = lr._1();
        nonLookupRules = lr._2();
    }

    public K runtimeProcess(K k) {
        return liftToKSequenceObj.convert(expandMacrosObj.expand(k));
    }

    private void prettyPrint(StringBuilder sb, Rule r) {
        String defaultRequires = "~>(#token(\"true\",Bool))";
        String defaultEnsures = "~>(#token(\"true\",Bool))";
        String body = r.body().toString();
        String requires = r.requires().toString();
        String ensures = r.ensures().toString();
        sb.append("Body: ");
        sb.append(body);
        sb.append("\n");
        if(! requires.equals(defaultRequires)) {
            sb.append("Requires: ");
            sb.append(r.requires().toString());
            sb.append("\n");
        }
        if(! ensures.equals(defaultEnsures)) {
            sb.append("Ensures: ");
            sb.append(r.ensures().toString());
            sb.append("\n");
        }
        sb.append("\n");        
    }

    private void prettyPrint(StringBuilder sb, Sort s) {
        sb.append(s.toString());
        sb.append("\n");
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rules:\n");
        for(Rule r : rules) {
            prettyPrint(sb, r);
        }
        sb.append("\n");
        sb.append("\n");
        sb.append("Sorts:\n");
        for(Sort s : definedSorts) {
            prettyPrint(sb, s);
        }
        sb.append("\n");
        sb.append("\n");
        sb.append("KLabels:\n");
        for(KLabel l : definedKLabels) {
            sb.append(l.toString());
            sb.append("\n");
        } 
        sb.append("\n");
        sb.append("\n");
        sb.append("Function Rules:\n");
        //        sb.append(functionRules.toString());
        sb.append("<omitted for brevity>\n");
        sb.append("\n");
        sb.append("Function Set:\n");
        sb.append(functionSet.toString());
        sb.append("\n");
        sb.append("Function Order:\n");
        sb.append(functionOrder.toString());
        sb.append("\n");
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

    private ModuleTransformer deconstructIntsMT() {
        return ModuleTransformer.fromSentenceTransformer(deconstructIntsObj::convert, deconstructIntsStr);
    }

    private ModuleTransformer expandMacrosMT() {
        return ModuleTransformer.fromSentenceTransformer(expandMacrosObj::expand, expandMacrosStr);
    }
}
