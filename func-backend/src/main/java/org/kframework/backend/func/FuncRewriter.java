package org.kframework.backend.func;

import com.google.inject.Inject;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.BiFunction;

import org.kframework.Rewriter;
import org.kframework.attributes.Source;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.DefinitionScoped;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * @author: Remy Goldschmidt
 */
@DefinitionScoped
public class FuncRewriter implements Function<Module, Rewriter> {
    private final KExceptionManager kem;
    private final FileUtil files;
    private final CompiledDefinition def;
    private final DefinitionToFunc converter;

    private static final String ocamlPackages = "zarith,str";
    private static final String pgmFileName   = "pgm.ml";

    private final File
        compileDirectory, compileOutFile, compileErrFile,
        runtimeDirectory, runtimeOutFile, runtimeErrFile,
        kompileDirectory, kompileDefFile;

    private static final String
        compileDirectoryName, compileOutFileName, compileErrFileName,
        runtimeDirectoryName, runtimeOutFileName, runtimeErrFileName,
        binaryOutputFileName, kompileDefFileName;

    static {
        compileDirectoryName = ".";
        compileOutFileName   = "compile.out";
        compileErrFileName   = "compile.err";
        runtimeDirectoryName = ".";
        runtimeOutFileName   = "run.out";
        runtimeErrFileName   = "run.err";
        binaryOutputFileName = "./a.out";
        kompileDefFileName   = "def.cmo";
    }

    // If you don't use ocamlfind, you will want to change this
    private static final String[] ocamlCmd = new String[]{
        "ocamlfind",
        "ocamlc",
        "-package",
        ocamlPackages,
        "-linkpkg",
        "-dllpath-all",
        "-g"
    };

    @Inject
    public FuncRewriter(KExceptionManager kem,
                        FileUtil files,
                        GlobalOptions globalOptions,
                        KompileOptions kompileOptions,
                        CompiledDefinition def) {
        this.kem = kem;
        this.files = files;
        this.def = def;
        this.converter = new DefinitionToFunc(kem, files, globalOptions, kompileOptions);

        this.compileDirectory = files.resolveTemp(compileDirectoryName);
        this.compileErrFile   = files.resolveTemp(compileErrFileName);
        this.compileOutFile   = files.resolveTemp(compileOutFileName);
        this.runtimeDirectory = files.resolveTemp(runtimeDirectoryName);
        this.runtimeErrFile   = files.resolveTemp(runtimeErrFileName);
        this.runtimeOutFile   = files.resolveTemp(runtimeOutFileName);

        this.kompileDirectory = files.resolveKompiled(".");
        this.kompileDefFile   = files.resolveKompiled(kompileDefFileName);

        converter.convert(def);
    }

    @Override
    public Rewriter apply(Module module) {
        if(!module.equals(def.executionModule())) {
            nonExecutionModuleError();
        }

        return new Rewriter() {
            @Override
            public K execute(K k, Optional<Integer> depth) {
                return executeOCaml(k, depth);
            }
        };
    }

    private K executeOCaml(K k, Optional<Integer> depth) {
        generateOCaml(k, depth);
        compileOCaml();
        runOCaml();
        return parseOCamlOutput();
    }

    private void generateOCaml(K k, Optional<Integer> depth) {
        String ocaml = converter.convert(k, depth.orElse(-1));
        files.saveToTemp(pgmFileName, ocaml);
    }

    private void compileOCaml() {
        try {
            Process p = startCompileProcess();

            try {
                int exit = p.waitFor();
                if(exit != 0) { compileFailedError(exit); }
            } catch(InterruptedException e) {
                compileInterruptedError(e);
            }
        } catch(IOException e) {
            compileIOError(e);
        }
    }

    private void runOCaml() {
        try {
            Process p = startRuntimeProcess();

            try {
                int exit = p.waitFor();
                if(exit != 0) { runtimeFailedError(exit); }
            } catch(InterruptedException e) {
                runtimeInterruptedError(e);
            }
        } catch(IOException e) {
            runtimeIOError(e);
        }
    }

    private K parseOCamlOutput() {
        String output = files.loadFromTemp(runtimeOutFileName);
        Module kseqSymbolic = def.getParsedDefinition().getModule("KSEQ-SYMBOLIC").get();
        BiFunction<String, Source, K> parser = def.getParser(kseqSymbolic, Sorts.K(), kem);
        Source ocamlSrc = Source.apply("generated by OCaml output");

        return parser.apply(output, ocamlSrc);
    }

    private Process startRuntimeProcess() throws IOException {
        return startProcess(files.getProcessBuilder(),
                            runtimeDirectory,
                            runtimeErrFile,
                            runtimeOutFile,
                            getRuntimeCommandLine());
    }

    private Process startCompileProcess() throws IOException {
        return startProcess(files.getProcessBuilder(),
                            compileDirectory,
                            compileErrFile,
                            compileOutFile,
                            getCompileCommandLine());
    }

    private static Process startProcess(ProcessBuilder pb,
                                        File dir,
                                        File stderr,
                                        File stdout,
                                        String... cmd) throws IOException {
        return pb.command(cmd)
                 .directory(dir)
                 .redirectError(stderr)
                 .redirectOutput(stdout)
                 .start();
    }

    private String[] getCompileCommandLine() {
        int pbListLength = ocamlCmd.length + 10; // padding
        List<String> pbList = Lists.newArrayListWithCapacity(pbListLength);

        pbList = FuncUtil.addMany(pbList, ocamlCmd);
        pbList.add("-I");
        pbList.add(kompileDirectory.getAbsolutePath());
        pbList.add(kompileDefFile.getAbsolutePath());
        pbList.add(pgmFileName);

        return pbList.toArray(new String[pbList.size()]);
    }

    private String[] getRuntimeCommandLine() {
        return new String[]{ binaryOutputFileName };
    }

    private void nonExecutionModuleError() {
        throw kemCriticalErrorF("Invalid module specified for rewriting.\n" +
                                "Functional backend only supports rewriting\n" +
                                "over the definition's main module.");
    }

    private void compileFailedError(int exitCode) {
        System.err.println(files.loadFromTemp(compileErrFileName));
        throw kemCriticalErrorF("Failed to compile program to OCaml.\n" +
                                "See output for error information.\n" +
                                "OCaml exit code: %d\n", exitCode);
    }

    private void runtimeFailedError(int exitCode) {
        System.err.println(files.loadFromTemp(runtimeErrFileName));
        throw kemCriticalErrorF("Failed to execute program in OCaml.\n" +
                                "Rerun with --debug and examine the\n" +
                                "temporary directory for information\n" +
                                "Program exit code: %d\n", exitCode);
    }

    private void compileInterruptedError(InterruptedException e) {
        Thread.currentThread().interrupt();
        throw kemCriticalErrorF(e, "OCaml compile process interrupted.");
    }

    private void runtimeInterruptedError(InterruptedException e) {
        Thread.currentThread().interrupt();
        throw kemCriticalErrorF(e, "Generated OCaml code interrupted.");
    }

    private void compileIOError(IOException e) {
        throw kemCriticalErrorF(e, "Failed to run OCaml compiler: %s", e.getMessage());
    }

    private void runtimeIOError(IOException e) {
        throw kemCriticalErrorF(e, "Failed to run generated OCaml program: %s", e.getMessage());
    }
}
