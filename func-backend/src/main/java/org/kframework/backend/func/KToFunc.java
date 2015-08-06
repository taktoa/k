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

    private SyntaxBuilder executeSB(K k, int depth, String outFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("run")
            .addArgument(newsb(convertRuntime(k)))
            .addArgument(newsbv(Integer.toString(depth)))
            .endApplication();
        return genRuntime(newsbApp("output_string",
                                   newsbn("out"),
                                   newsbApp("print_k",
                                            newsb()
                                            .beginTry()
                                            .addTryValue(tryValueSB)
                                            .beginTryEquations()
                                            .addTryEquation(newsbn("Stuck c'"),
                                                            newsbn("c'"))
                                            .endTryEquations()
                                            .endTry())),
                          outFile);
    }

    private SyntaxBuilder matchSB(K k, Rule rule, String outFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("print_subst")
            .addArgument("file1")
            .beginArgument()
            .beginApplication()
            .addFunction("try_match")
            .beginArgument()
            .addValue(convertRuntime(k))
            .endArgument()
            .endApplication()
            .endArgument()
            .endApplication();
        SyntaxBuilder printOutSB = newsbApp("output_string",
                                            newsbn("file1"),
                                            newsbv(enquoteString("0\n")));
        return genRuntime(newsb()
                          .beginTry()
                          .addTryValue(tryValueSB)
                          .beginTryEquations()
                          .addTryEquation(newsbn("Stuck c"), printOutSB)
                          .endTryEquations()
                          .endTry(),
                          rule,
                          outFile);
    }

    private SyntaxBuilder executeAndMatchSB(K k, int depth,
                                            Rule rule,
                                            String outFile,
                                            String substFile) {
        SyntaxBuilder tryValueSB =
            newsb()
            .beginApplication()
            .addFunction("print_subst")
            .addArgument("file2")
            .beginArgument()
            .beginApplication()
            .addFunction("try_match")
            .beginArgument()
            .beginLetExpression()
            .beginLetEquations()
            .addLetEquation(newsbn("res"),
                            newsbApp("run",
                                     newsbv(convertRuntime(k)),
                                     newsbv(depth)))
            .endLetEquations()
            .beginLetScope()
            .addSequence(newsbApp("output_string",
                                  newsbn("file1"),
                                  newsbApp("print_k", newsbn("res"))),
                         newsbn("res"))
            .endLetScope()
            .endLetExpression()
            .endArgument()
            .endApplication()
            .endArgument()
            .endApplication();
        SyntaxBuilder printOutSB   = newsbApp("output_string",
                                              newsbn("file1"),
                                              newsbApp("print_k", newsbn("c")));
        SyntaxBuilder printSubstSB = newsbApp("output_string",
                                              newsbn("file2"),
                                              newsbv(enquoteString("0\n")));
        return genRuntime(newsb()
                          .beginTry()
                          .addTryValue(tryValueSB)
                          .beginTryEquations()
                          .addTryEquation(newsbn("Stuck c"),
                                          newsb().addSequence(printOutSB,
                                                              printSubstSB))
                          .endTryEquations()
                          .endTry(),
                          rule,
                          outFile, substFile);
    }

    private SyntaxBuilder genRuntime(SyntaxBuilder body,
                                     String... paths) {
        return newsb()
            .append(genImports())
            .append(genFileDefs(paths))
            .append(genConfig())
            .append(runCode(body));
    }

    private SyntaxBuilder genRuntime(SyntaxBuilder body,
                                     Rule r,
                                     String... paths) {
        return newsb()
            .append(genImports())
            .append(genTryMatch(r))
            .append(genFileDefs(paths))
            .append(genConfig())
            .append(runCode(body));
    }

    private SyntaxBuilder runCode(SyntaxBuilder code) {
        return newsb().addGlobalLet(newsbp("_"), code);
    }

    private SyntaxBuilder genConfig() {
        return newsb().addGlobalLet(newsb().addName("config"), bottomSB);
    }

    private SyntaxBuilder genFileDefs(String... paths) {
        SyntaxBuilder sb;
        int i = 0;
        for(String path : paths) {
            sb.addGlobalLet(newsb().addName(String.format("file%d", i++)),
                            newsb().addApplication("open_out",
                                                   newsbv(enquoteString(path))));
        }
        return sb;
    }

    private SyntaxBuilder genTryMatch() {
        return newsb()
            .append("let try_match (c: k) : k Subst.t =")
            .append("let config = c in")
            .append("match c with ")
            .append("\n")
            .append(convertFunction(Collections.singletonList(convert(preproc, r)),
                                    "try_match", RuleType.PATTERN))
            .append("| _ -> raise(Stuck c)")
            .append("\n");
    }


    private SyntaxBuilder convertRuntime(K k) {
        return genVisitor(new VarInfo(),
                          true,
                          false,
                          false).apply(preproc.runtimeProcess(k));
    }
}
