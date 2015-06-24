package org.kframework.backend.func;

import com.google.inject.Inject;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * @author: Remy Goldschmidt
 */
public class FuncBackend implements Consumer<CompiledDefinition> {

    private final KExceptionManager kem;
    private final FileUtil files;
    private final GlobalOptions globalOptions;
    private final KompileOptions kompileOptions;

    @Inject
    public FuncBackend(KExceptionManager kem, FileUtil files, GlobalOptions globalOptions, KompileOptions kompileOptions) {
        this.kem = kem;
        this.files = files;
        this.globalOptions = globalOptions;
        this.kompileOptions = kompileOptions;
    }

    @Override
    public void accept(CompiledDefinition compiledDefinition) {
        String ocaml = new DefinitionToFunc(kem, files, globalOptions, kompileOptions).convert(compiledDefinition);
        files.saveToKompiled("def.ml", ocaml);
        try {
            String packages = "zarith"; // comma-separated
            ProcessBuilder ocamloptBuilder =
                files.getProcessBuilder()
                     .command("ocamlfind",
                              "ocamlc",
                              "-dllpath-all",
                              "-linkpkg",
                              "-package",
                              packages,
                              "-c",
                              "-g",
                              "def.ml");

            ocamloptBuilder = ocamloptBuilder.directory(files.resolveKompiled("."));
            ocamloptBuilder = ocamloptBuilder.inheritIO();

            Process ocamlopt = ocamloptBuilder.start();

            int exit = ocamlopt.waitFor();
            if (exit != 0) {
                throw KEMException.criticalError("ocamlopt returned nonzero exit code: " + exit + "\nExamine output to see errors.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KEMException.criticalError("Ocaml process interrupted.", e);
        } catch (IOException e) {
            throw KEMException.criticalError("Error starting ocamlopt process: " + e.getMessage(), e);
        }
    }
}
