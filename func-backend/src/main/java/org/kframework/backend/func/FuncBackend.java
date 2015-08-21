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
import java.util.Map;
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
    private final FuncOptions options;

    private static final String ocamlPackages = "gmp,zarith";

    private static final String
        definitionFileName,    constantsFileName,
        lexerInputFileName,    parserInputFileName,
        preludeFileName,       lexerFileName,        parserFileName,
        kompileOutputFileName, kompileErrorFileName, kompileDirectoryName;

    private final File
        definitionFile,    constantsFile,
        preludeFile,       preludeInclude,
        lexerFile,         lexerInclude,
        parserFile,        parserInclude,
        lexerInputFile,    parserInputFile,
        kompileOutputFile, kompileErrorFile,
        kompileDirectory;

    static {
        definitionFileName     = "def.ml";
        constantsFileName      = "constants.ml";
        preludeFileName        = "prelude.ml";
        lexerFileName          = "lexer.ml";
        parserFileName         = "parser.ml";
        lexerInputFileName     = "lexer.mll";
        parserInputFileName    = "parser.mly";
        kompileOutputFileName  = "kompile.out";
        kompileErrorFileName   = "kompile.err";
        kompileDirectoryName   = ".";
    }

    // If you don't use ocamlfind, you will want to change this
    private static final String[] ocamlCmd = new String[]{
        "ocamlfind",
        "ocamlopt",
        "-dllpath-all",
        "-linkpkg",
        "-package", ocamlPackages,
        "-w", "-26-11",
        "-inline", "20",
        "-c",
        "-g",
        constantsFileName,
        preludeFileName,
        definitionFileName,
        parserFileName + "i",
        parserFileName,
        lexerFileName
    };

    @Inject
    public FuncBackend(KExceptionManager kem,
                       FileUtil files,
                       GlobalOptions globalOptions,
                       KompileOptions kompileOptions,
                       FuncOptions options) {
        this.processBuilder = files.getProcessBuilder();

        this.converterGen = def -> {
            PreprocessedKORE ppk = new PreprocessedKORE(def, kem, files,
                                                        globalOptions,
                                                        kompileOptions,
                                                        options);
            return new DefinitionToFunc(ppk, kem);
        };

        this.options = options;

        this.definitionFile    = files.resolveKompiled(definitionFileName);
        this.constantsFile     = files.resolveKompiled(constantsFileName);
        this.preludeFile       = files.resolveKompiled(preludeFileName);
        this.lexerFile         = files.resolveKompiled(lexerFileName);
        this.parserFile        = files.resolveKompiled(parserFileName);
        this.lexerInputFile    = files.resolveKompiled(lexerInputFileName);
        this.parserInputFile   = files.resolveKompiled(parserInputFileName);
        this.preludeInclude    = files.resolveKBase("include/ocaml/prelude.ml");
        this.lexerInclude      = files.resolveKBase("include/ocaml/lexer.mll");
        this.parserInclude     = files.resolveKBase("include/ocaml/parser.mly");
        this.kompileOutputFile = files.resolveTemp(kompileOutputFileName);
        this.kompileErrorFile  = files.resolveTemp(kompileErrorFileName);
        this.kompileDirectory  = files.resolveKompiled(kompileDirectoryName);
    }

    @Override
    public void accept(CompiledDefinition def) {
        generateEnvironment();
        generateOCaml(def);
        lexAndParseOCaml();
        compileOCaml();
    }

    private void generateEnvironment() {
        Map<String, String> ocamlFindCommands = newHashMap();
        ocamlFindCommands.put("ocamlc",   "ocamlc.opt");
        ocamlFindCommands.put("ocamlopt", "ocamlopt.opt");

        processBuilder
            .environment()
            .put("OCAMLFIND_COMMANDS",
                 ocamlFindCommands
                 .entrySet()
                 .stream()
                 .map(x -> x.getKey() + "=" + x.getValue())
                 .collect(joiningC(" ")));
    }

    private void generateOCaml(CompiledDefinition def) {
        converter = converterGen.apply(def);
        try {
            FileUtil.save(constantsFile,  converter.getConstants());
            FileUtil.save(definitionFile, converter.getDefinition());
            FileUtils.copyFile(preludeInclude, preludeFile);
            FileUtils.copyFile(lexerInclude,   lexerInputFile);
            FileUtils.copyFile(parserInclude,  parserInputFile);
        } catch (IOException e) {
            generateIOError(e);
        }
    }

    private void lexAndParseOCaml() {
        try {
            Process lexProc = startLexProcess();
            int exitLex = lexProc.waitFor();
            if(exitLex != 0) { lexFailedError(exitLex); }

            Process parseProc = startParseProcess();
            int exitParse = parseProc.waitFor();
            if(exitParse != 0) { parseFailedError(exitParse); }
        } catch(InterruptedException e) {
            lexAndParseInterruptedError(e);
        } catch (IOException e) {
            lexAndParseIOError(e);
        }
    }

    private void compileOCaml() {
        try {
            FileUtil.save(kompileOutputFile, "");
            FileUtil.save(kompileErrorFile, "");

            Process p = startCompileProcess();

            int exit = p.waitFor();

            outprintfln("%s", FileUtil.load(kompileOutputFile));
            outprintfln("%s", FileUtil.load(kompileErrorFile));

            FileUtils.forceDelete(kompileOutputFile);
            FileUtils.forceDelete(kompileErrorFile);

            if(exit != 0) { compileFailedError(exit); }
        } catch(InterruptedException e) {
            compileInterruptedError(e);
        } catch (IOException e) {
            compileIOError(e);
        }
    }

    private Process startLexProcess() throws IOException {
        return startProcess(processBuilder,
                            kompileDirectory,
                            "ocamllex", lexerInputFileName);
    }

    private Process startParseProcess() throws IOException {
        return startProcess(processBuilder,
                            kompileDirectory,
                            "ocamlyacc", parserInputFileName);
    }

    private Process startCompileProcess() throws IOException {
        return startProcess(processBuilder,
                            kompileDirectory,
                            kompileErrorFile,
                            kompileOutputFile,
                            getCompileCommandLine());
    }

    private String[] getCompileCommandLine() {
        return ocamlCmd;
    }

    private void generateIOError(IOException e) {
        throw kemCriticalErrorF(e, "Error saving OCaml files: %s",
                                e.getMessage());
    }

    private void lexFailedError(int exit) {
        throw kemCriticalErrorF("OCaml lexer returned nonzero exit code: %s\n" +
                                "Examine output to see errors.", exit);
    }

    private void parseFailedError(int exit) {
        throw kemCriticalErrorF("OCaml parser returned nonzero exit code: %s\n" +
                                "Examine output to see errors.", exit);
    }

    private void lexAndParseInterruptedError(InterruptedException e) {
        Thread.currentThread().interrupt();
        throw kemCriticalErrorF(e, "OCaml lexer / parser process interrupted.");
    }

    private void lexAndParseIOError(IOException e) {
        throw kemCriticalErrorF(e, "Error starting OCaml lexer / parser process: %s",
                                e.getMessage());
    }

    private void compileFailedError(int exit) {
        throw kemCriticalErrorF("OCaml compiler returned nonzero exit code: %s\n" +
                                "Examine output to see errors.", exit);
    }

    private void compileInterruptedError(InterruptedException e) {
        Thread.currentThread().interrupt();
        throw kemCriticalErrorF(e, "OCaml process interrupted.");
    }

    private void compileIOError(IOException e) {
        throw kemCriticalErrorF(e, "Error starting OCaml compiler process: %s",
                                e.getMessage());
    }
}
