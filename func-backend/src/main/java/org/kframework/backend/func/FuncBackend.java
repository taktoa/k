// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.inject.Inject;
import com.google.common.base.Stopwatch;

import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * Converts KORE for the functional backend at kompile time
 *
 * @author Remy Goldschmidt
 */
public class FuncBackend implements Consumer<CompiledDefinition> {
    private DefinitionToFunc converter;

    private final Function<CompiledDefinition, DefinitionToFunc> converterGen;
    private final ProcessBuilder processBuilder;

    private static final String ocamlPackages = "zarith";

    private static final String
        kompileOutputFileName, kompileErrorFileName,
        kompileDirectoryName, defSourceFileName;

    private final File
        kompileOutputFile, kompileErrorFile,
        kompileDirectory, defSourceFile;

    static {
        kompileOutputFileName  = "kompile.out";
        kompileErrorFileName   = "kompile.err";
        kompileDirectoryName   = ".";
        defSourceFileName      = "def.ml";
    }

    // If you don't use ocamlfind, you will want to change this
    private static final String[] ocamlCmd = new String[]{
        "ocamlfind",
        "ocamlc",
        "-dllpath-all",
        "-linkpkg",
        "-package",
        ocamlPackages,
        "-c",
        "-g",
        defSourceFileName
    };

    @Inject
    public FuncBackend(KExceptionManager kem,
                       FileUtil files,
                       GlobalOptions globalOptions,
                       KompileOptions kompileOptions) {
        this.processBuilder = files.getProcessBuilder();

        this.converterGen = def -> {
            PreprocessedKORE ppk = new PreprocessedKORE(def, kem, files,
                                                        globalOptions,
                                                        kompileOptions);
            return new DefinitionToFunc(kem, ppk);
        };

        this.kompileOutputFile = files.resolveTemp(kompileOutputFileName);
        this.kompileErrorFile  = files.resolveTemp(kompileErrorFileName);
        this.kompileDirectory  = files.resolveKompiled(kompileDirectoryName);
        this.defSourceFile     = files.resolveKompiled(defSourceFileName);
    }

    @Override
    public void accept(CompiledDefinition def) {
        generateOCamlDef(def);
        compileOCamlDef();
    }

    private void generateOCamlDef(CompiledDefinition def) {
        converter = converterGen.apply(def);
        String ocaml = converter.genOCaml();
        FileUtil.save(defSourceFile, ocaml);
    }

    private void compileOCamlDef() {
        try {
            FileUtil.save(kompileOutputFile, "");
            FileUtil.save(kompileErrorFile, "");

            Process p = startDefCompileProcess();

            int exit = p.waitFor();

            outprintfln("%s", FileUtil.load(kompileOutputFile));
            outprintfln("%s", FileUtil.load(kompileErrorFile));

            FileUtils.forceDelete(kompileOutputFile);
            FileUtils.forceDelete(kompileErrorFile);

            if(exit != 0) { defCompileFailedError(exit); }
        } catch(InterruptedException e) {
            defCompileInterruptedError(e);
        } catch (IOException e) {
            defCompileIOError(e);
        }
    }

    private Process startDefCompileProcess() throws IOException {
        return startProcess(processBuilder,
                            kompileDirectory,
                            kompileErrorFile,
                            kompileOutputFile,
                            getDefCompileCommandLine());
    }

    private String[] getDefCompileCommandLine() {
        return ocamlCmd;
    }

    private void defCompileFailedError(int exit) {
        throw kemCriticalErrorF("OCaml compiler returned nonzero exit code: %s\n" +
                                "Examine output to see errors.", exit);
    }

    private void defCompileInterruptedError(InterruptedException e) {
        Thread.currentThread().interrupt();
        throw kemCriticalErrorF(e, "Ocaml process interrupted.");
    }

    private void defCompileIOError(IOException e) {
        throw kemCriticalErrorF(e, "Error starting OCaml compiler process: %s", e.getMessage());
    }
}
