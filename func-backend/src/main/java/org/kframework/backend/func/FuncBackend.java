package org.kframework.backend.func;

import com.google.inject.Inject;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.io.IOException;
import java.io.File;
import java.util.function.Consumer;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * Converts K to OCaml at kompile time
 *
 * @author: Remy Goldschmidt
 */
public class FuncBackend implements Consumer<CompiledDefinition> {
    private final DefinitionToFunc converter;
    private final ProcessBuilder processBuilder;

    private static final String ocamlPackages = "zarith";

    private static final String
        kompileDirectoryName, defSourceFileName;

    private final File
        kompileDirectory, defSourceFile;

    static {
        kompileDirectoryName  = ".";
        defSourceFileName     = "def.ml";
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

        this.converter = new DefinitionToFunc(kem, files, globalOptions, kompileOptions);

        this.kompileDirectory  = files.resolveKompiled(kompileDirectoryName);
        this.defSourceFile     = files.resolveKompiled(defSourceFileName);
    }

    @Override
    public void accept(CompiledDefinition def) {
        generateOCamlDef(def);
        compileOCamlDef();
    }

    private void generateOCamlDef(CompiledDefinition def) {
        String ocaml = converter.convert(def);
        FileUtil.save(defSourceFile, ocaml);
    }

    private void compileOCamlDef() {
        try {
            Process p = startDefCompileProcess();

            try {
                int exit = p.waitFor();
                if(exit != 0) { defCompileFailedError(exit); }
            } catch(InterruptedException e) {
                defCompileInterruptedError(e);
            }
        } catch (IOException e) {
            defCompileIOError(e);
        }
    }

    private Process startDefCompileProcess() throws IOException {
        return startProcess(processBuilder,
                            kompileDirectory,
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
