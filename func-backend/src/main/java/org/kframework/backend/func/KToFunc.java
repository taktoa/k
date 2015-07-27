// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;

import org.kframework.kompile.CompiledDefinition;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.main.GlobalOptions;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * A way to partially evaluate the DefinitionToFunc constructor
 *
 * @author Remy Goldschmidt
 */
public class KToFunc {
    private final PreprocessedKORE preproc;

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
     * Generate OCaml from the given KORE
     */
    public String convert(K k, int depth) {
        return convertSB(k, depth).toString();
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
