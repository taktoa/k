package org.kframework.backend.func;

import com.google.inject.Inject;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import org.kframework.Rewriter;
import org.kframework.attributes.Source;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KEMException;
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

    // The packages used by the generated OCaml
    private static final String ocamlPackages = "zarith,str";

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
        converter.convert(def);
    }

    @Override
    public Rewriter apply(Module module) {
        if(!module.equals(def.executionModule())) {
            throw KEMException.criticalError("Invalid module specified for rewriting.\n" +
                                             "Functional backend only supports rewriting\n" +
                                             "over the definition's main module.");
        }
        return new Rewriter() {
            @Override
            public K execute(K k, Optional<Integer> depth) {
                String ocaml = converter.convert(k, depth.orElse(-1));
                files.saveToTemp("pgm.ml", ocaml);
                try {

                    ProcessBuilder pb = files.getProcessBuilder();
                    int padding = 10;
                    List<String> pbList = Lists.newArrayListWithCapacity(ocamlCmd.length + padding);
                    pbList.addAll(Arrays.asList(ocamlCmd));
                    pbList.add("-I");
                    pbList.add(files.resolveKompiled(".").getAbsolutePath());
                    pbList.add(files.resolveKompiled("def.cmo").getAbsolutePath());
                    pbList.add("pgm.ml");
                    String[] pbArr = new String[pbList.size()];
                    for(int i = 0; i < pbList.size(); i++) {
                        pbArr[i] = pbList.get(i);
                    }

                    pb = pb.command(pbArr);

                    Process p = pb.directory(files.resolveTemp("."))
                                  .redirectError(files.resolveTemp("compile.err"))
                                  .redirectOutput(files.resolveTemp("compile.out"))
                                  .start();

                    int exit = p.waitFor();
                    if(exit != 0) {
                        System.err.println(files.loadFromTemp("compile.err"));
                        throw KEMException.criticalError("Failed to compile program to ocaml.\n" +
                                                         "See output for error information.");
                    }
                    p = files.getProcessBuilder()
                             .command("./a.out")
                             .directory(files.resolveTemp("."))
                             .redirectError(files.resolveTemp("run.err"))
                             .redirectOutput(files.resolveTemp("run.out"))
                             .start();
                    exit = p.waitFor();
                    if(exit != 0) {
                        System.err.println(files.loadFromTemp("run.err"));
                        throw KEMException.criticalError("Failed to execute program in ocaml.\n" +
                                                         "Rerun with --debug and examine the\n" +
                                                         "temporary directory for information");
                    }
                    String output = files.loadFromTemp("run.out");
                    return def.getParser(def.getParsedDefinition().getModule("KSEQ-SYMBOLIC").get(),
                                         Sorts.K(),
                                         kem)
                              .apply(output, Source.apply("generated by ocaml output"));
                } catch (IOException e) {
                    throw KEMException.criticalError("Failed to start ocamlopt: " + e.getMessage(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw KEMException.criticalError("Ocaml process interrupted.", e);
                }
            }
        };
    }
}
