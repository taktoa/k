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

import static scala.compat.java8.JFunction.*;

/**
 * @author: Remy Goldschmidt
 */
// TODO(taktoa): add more staticness maybe
public class FuncPreprocessors {
    private static ConvertDataStructureToLookup convertLookupsObj;
    private static GenerateSortPredicateRules   generatePredicatesObj;
    private static LiftToKSequence              liftToKSequenceObj;
    private static DeconstructIntegerLiterals   deconstructIntsObj;
    private static ExpandMacros                 expandMacrosObj;

    private static Module executionModule;
    private static Definition kompiledDefinition;

    private static final String convertLookupsStr     = "Convert data structures to lookups";
    private static final String generatePredicatesStr = "Generate predicates";
    private static final String liftToKSequenceStr    = "Lift K into KSequence";
    private static final String deconstructIntsStr    = "Remove matches on integer literals in left hand side";
    private static final String expandMacrosStr       = "Expand macros rules";
    
    public FuncPreprocessors(CompiledDefinition def,
                             KExceptionManager kem,
                             FileUtil files,
                             GlobalOptions globalOptions,
                             KompileOptions kompileOptions) {
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
    }
    
    public Module modulePreprocess() {        
        // Steps are separated for debugging purposes, since with
        // the steps separated, the line number of the failing step
        // is made clear.
        // If this has a performance detriment (it probably does),
        // just turn it into one pipeline with andThen()
        // - taktoa (Remy Goldschmidt)
        Module step1 = deconstructIntsMT().apply(executionModule);
        Module step2 = convertLookupsMT().apply(step1);
        Module step3 = expandMacrosMT().apply(step2);
        Module step4 = generatePredicatesMT().apply(step3);
        Module step5 = liftToKSequenceMT().apply(step4);
        
        return step5;
    }

    public K korePreprocess(K k) {
        return liftToKSequenceObj.convert(expandMacrosObj.expand(k));
    }


    private static ModuleTransformer convertLookupsMT() {
        return ModuleTransformer.fromSentenceTransformer(convertLookupsObj::convert, convertLookupsStr);
    }

    // TODO(taktoa): figure out why I can't make this a ModuleTransformer
    private static Function1<Module, Module> generatePredicatesMT() {
        return func(generatePredicatesObj::gen);
    }

    private static ModuleTransformer liftToKSequenceMT() {
        return ModuleTransformer.fromSentenceTransformer(liftToKSequenceObj::convert, liftToKSequenceStr);
    }

    private static ModuleTransformer deconstructIntsMT() {
        return ModuleTransformer.fromSentenceTransformer(deconstructIntsObj::convert, deconstructIntsStr);
    }
    
    private static ModuleTransformer expandMacrosMT() {
        return ModuleTransformer.fromSentenceTransformer(expandMacrosObj::expand, expandMacrosStr);
    }
}
