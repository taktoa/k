// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.kframework.Rewriter;
import org.kframework.RewriterResult;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.indexing.IndexingTable;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.definition.Rule;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.JavaKRunState;
import org.kframework.definition.Module;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KVariable;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.Builtins;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.inject.RequestScoped;
import org.kframework.utils.options.SMTOptions;
import scala.Tuple2;
import scala.collection.JavaConversions;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by dwightguth on 5/6/15.
 */
@RequestScoped
public class InitializeRewriter implements Function<Module, Rewriter> {

    private final FileSystem fs;
    private final JavaExecutionOptions javaOptions;
    private final GlobalOptions globalOptions;
    private final KExceptionManager kem;
    private final SMTOptions smtOptions;
    private final Map<String, Provider<MethodHandle>> hookProvider;
    private final KompileOptions kompileOptions;
    private final KRunOptions krunOptions;
    private final FileUtil files;
    private final InitializeDefinition initializeDefinition;
    private static int NEGATIVE_VALUE = -1;

    @Inject
    public InitializeRewriter(
            FileSystem fs,
            JavaExecutionOptions javaOptions,
            GlobalOptions globalOptions,
            KExceptionManager kem,
            SMTOptions smtOptions,
            @Builtins Map<String, Provider<MethodHandle>> hookProvider,
            KompileOptions kompileOptions,
            KRunOptions krunOptions,
            FileUtil files,
            InitializeDefinition initializeDefinition) {
        this.fs = fs;
        this.javaOptions = javaOptions;
        this.globalOptions = globalOptions;
        this.kem = kem;
        this.smtOptions = smtOptions;
        this.hookProvider = hookProvider;
        this.kompileOptions = kompileOptions;
        this.krunOptions = krunOptions;
        this.files = files;
        this.initializeDefinition = initializeDefinition;
    }

    @Override
    public synchronized Rewriter apply(Module module) {
        GlobalContext initializingContext = new GlobalContext(fs, javaOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING);
        GlobalContext rewritingContext = new GlobalContext(fs, javaOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        Definition evaluatedDef = initializeDefinition.invoke(module, kem, initializingContext);
        rewritingContext.setDefinition(evaluatedDef);

        return new SymbolicRewriterGlue(evaluatedDef, kompileOptions, javaOptions, rewritingContext, kem);
    }

    public static class SymbolicRewriterGlue implements Rewriter {

        private final SymbolicRewriter rewriter;
        public final GlobalContext rewritingContext;
        private final KExceptionManager kem;

        public SymbolicRewriterGlue(Definition definition, KompileOptions kompileOptions, JavaExecutionOptions javaOptions, GlobalContext rewritingContext, KExceptionManager kem) {
            this.rewriter = new SymbolicRewriter(definition, kompileOptions, javaOptions, new KRunState.Counter());
            this.rewritingContext = rewritingContext;
            this.kem = kem;
        }

        @Override
        public RewriterResult execute(K k, Optional<Integer> depth) {
            KOREtoBackendKIL converter = new KOREtoBackendKIL(TermContext.of(rewritingContext));
            Term backendKil = KILtoBackendJavaKILTransformer.expandAndEvaluate(rewritingContext, kem, converter.convert(k));
            JavaKRunState result = (JavaKRunState) rewriter.rewrite(new ConstrainedTerm(backendKil, TermContext.of(rewritingContext, backendKil, BigInteger.ZERO)), rewritingContext.getDefinition().context(), depth.orElse(-1), false);
            return new RewriterResult(result.getStepsTaken(), result.getJavaKilTerm());
        }

        @Override
        public List<Map<KVariable, K>> match(K k, org.kframework.definition.Rule rule) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends Map<? extends KVariable, ? extends K>> search(K initialConfiguration, Optional<Integer> depth, Optional<Integer> bound, Rule pattern) {
            KOREtoBackendKIL converter = new KOREtoBackendKIL(TermContext.of(rewritingContext));
            Term javaTerm = KILtoBackendJavaKILTransformer.expandAndEvaluate(rewritingContext, kem, converter.convert(initialConfiguration));
            org.kframework.backend.java.kil.Rule javaPattern = converter.convert(Optional.empty(), pattern);
            List<Substitution<Variable, Term>> searchResults;
            searchResults = rewriter.search(javaTerm, javaPattern, bound.orElse(NEGATIVE_VALUE), depth.orElse(NEGATIVE_VALUE),
                    SearchType.STAR, TermContext.of(rewritingContext), false);
            return searchResults;
        }


        public Tuple2<K, List<Map<KVariable, K>>> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
            K res = execute(k, depth).k();
            return Tuple2.apply(res, match(res, rule));
        }

    }


    @DefinitionScoped
    public static class InitializeDefinition {

        private final Map<Module, Definition> cache = new LinkedHashMap<Module, Definition>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Module, Definition> eldest) {
                return this.size() > 20;
            }
        };

        public Definition invoke(Module module, KExceptionManager kem, GlobalContext initializingContext) {
            if (cache.containsKey(module)) {
                return cache.get(module);
            }
            Definition definition = new Definition(module, kem);

            TermContext termContext = TermContext.of(initializingContext);
            termContext.global().setDefinition(definition);

            JavaConversions.setAsJavaSet(module.attributesFor().keySet()).stream()
                    .map(l -> KLabelConstant.of(l.name(), definition))
                    .forEach(definition::addKLabel);
            definition.addKoreRules(module, termContext);

            Definition evaluatedDef = KILtoBackendJavaKILTransformer.expandAndEvaluate(termContext.global(), kem);

            evaluatedDef.setIndex(new IndexingTable(() -> evaluatedDef, new IndexingTable.Data()));
            cache.put(module, evaluatedDef);
            return evaluatedDef;
        }
    }
}
