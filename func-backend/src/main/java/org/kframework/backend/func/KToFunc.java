// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;

import org.kframework.kompile.CompiledDefinition;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.main.GlobalOptions;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.definition.Rule;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * A way to partially evaluate the DefinitionToFunc constructor
 *
 * @author Remy Goldschmidt
 */
public class KToFunc {
    private final PreprocessedKORE preproc;

    private static final SyntaxBuilder imports;

    static {
        imports = newsb()
            .addImport("Prelude")
            .addImport("Constants")
            .addImport("Prelude.K")
            .addImport("Gmp")
            .addImport("Def");
    }

    /**
     * Constructor for KToFunc
     */
    public KToFunc(PreprocessedKORE preproc) {
        this.preproc = preproc;
    }

    /**
     * Alternate constructor for KToFunc
     */
    public KToFunc(CompiledDefinition compDef,
                   KExceptionManager kem,
                   FileUtil files,
                   GlobalOptions globalOptions,
                   KompileOptions kompileOptions) {
        this(new PreprocessedKORE(compDef, kem, files,
                                  globalOptions, kompileOptions));
    }

    /**
     * Generate execution OCaml from the given KORE
     */
    public String execute(K k, int depth, String outputPath) {
        return executeSB(k, depth, outputPath).toString();
    }

    /**
     * Generate search OCaml from the given KORE
     */
    public String match(K k, Rule rule, String outputPath) {
        return matchSB(k, rule, outputPath).toString();
    }

    /**
     * Generate execution and search OCaml from the given KORE
     */
    public String executeAndMatch(K k, int depth,
                                  Rule rule,
                                  String outputPath,
                                  String substPath) {
        return executeAndMatchSB(k, depth, rule,
                                 outputPath, substPath).toString();
    }


    private SyntaxBuilder executeSB(K k, int depth, String outputPath) {
        outprintfln("DBG: executeSB");
        SyntaxBuilder sb = convertSB(k, depth);
        return sb;
    }

    private SyntaxBuilder matchSB(K k, Rule rule, String outputPath) {
        outprintfln("DBG: matchSB");
        SyntaxBuilder sb = imports;
        return sb;
    }

    private SyntaxBuilder executeAndMatchSB(K k, int depth,
                                            Rule rule,
                                            String outputPath,
                                            String substPath) {
        outprintfln("DBG: executeAndMatchSB");
        SyntaxBuilder sb = imports;
        return sb;
    }


    private SyntaxBuilder convertSB(K k, int depth) {
        SyntaxBuilder sb = newsb();

        FuncVisitor convVisitor = new FuncVisitor(preproc,
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
        return sb;
    }
}
