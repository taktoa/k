// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.ocaml;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Comparator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.SetMultimap;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kil.Definition;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kompile.Kompile;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.GenerateSortPredicates;
import org.kframework.kore.compile.LiftToKSequence;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.krun.KRun;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.StringUtil;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.algorithms.SCCTarjan;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Function1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kframework.backend.ocaml.compile.DefinitionToOcaml;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class OcamlBackendTest {
    private static final String tempDir       = "k-ocaml-tmp-";
    private static final GlobalOptions gopts  = new GlobalOptions();
    private static final KompileOptions kopts = new KompileOptions();

    private FileUtil futil;
    private File kfile;
    private CompiledDefinition cdef;
    private KExceptionManager kem;
    private String mainModuleName;
    private String programModuleName;
    private String testPgm;

    // Create a temp directory, copy the given file to it
    // and set futil to a sane value based on this directory.
    // Also, register a shutdown hook for deleting the temp dir.
    private void tempFileUtil(File kdef) throws IOException {
        File f = Files.createTempDirectory(tempDir).toFile();
        kfile = new File(f.getPath() + File.separator + kdef.getName());
        Files.copy(kdef.toPath(), kfile.toPath());
        Provider<File> pf = Providers.of(f);
        futil = new FileUtil(f, pf, f, pf, gopts, System.getenv());
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        FileUtils.deleteDirectory(f);
                    } catch(IOException e) {
                        System.out.println("Failed to delete tmp dir: " + f);
                        System.out.println(e);
                    }
                }
            });
    }


    // Get the parsed test program
    private K parsedTestProgram() {
        Source source = Source.apply("generated by DefinitionToOcaml");
        return cdef.getProgramParser(kem).apply(testPgm, source);
    }

    // Initialize all the member variables
    private void testInit() throws IOException {
        kem = new KExceptionManager(gopts);

        mainModuleName    = "TEST"; // FIXME
        programModuleName = "TEST-PROGRAMS"; // FIXME
        testPgm = "int n, .Ids; n = 10; while(0 <= n) { n = n + -1; }";

        tempFileUtil(new File("src/test/resources/kore_imp_tiny.k"));

        Kompile komp = new Kompile(kopts, futil, kem, false);
        cdef = komp.run(kfile, mainModuleName, programModuleName, Sorts.K());
    }

    // This should just print out the OCaml corresponding to the test program
    @Test
    public void testOcaml() throws IOException {
        testInit();
        K program = parsedTestProgram();
        KToken pgmtk = KToken(Sorts.KConfigVar(), "$PGM");
        Map initMap = Collections.singletonMap(pgmtk, program);
        KRun krun = new KRun(kem, futil);
        K runpgm = krun.plugConfigVars(cdef, initMap);

        DefinitionToOcaml conv = new DefinitionToOcaml();

//        String ocaml = conv.convert(cdef);
//        System.out.println(ocaml);
//        String pgm = conv.convert(runpgm);
//        System.out.println(pgm);

        assertEquals(true, true); // TODO(taktoa): replace with actual unit test.

    }
}
