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
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
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
import org.kframework.backend.java.symbolic.JavaSymbolicBackend;
import org.kframework.krun.KRun;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.StringUtil;
import org.kframework.utils.algorithms.SCCTarjan;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Function1;

import java.io.File;
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

    private CompiledDefinition kompile(KExceptionManager kem, File kdef) {
        KompileOptions kopts = new KompileOptions();

        Stopwatch sw = null;
        Loader loader = null;
        Provider<RuleIndex> rix = null;
        Provider<KILtoBackendJavaKILTransformer> xfrm = null;
        Provider<DefinitionLoader> dfl = null;
        FileUtil futil = null;
        Context ctx = new Context();
        Definition javaDef = dfl.loadDefinition(defFile, defModule, ctx);
        Backend backend = new JavaSymbolicBackend(sw, ctx, kopts, loader, rix, xfrm, futil, kem);
        CompilerSteps<Definition> steps = b.getCompilationSteps();

        try {
            javaDef = steps.compile(javaDef, backend.getDefaultStep());
        } catch(CompilerStepDone e) {
            javaDef = (Definition) e.getResult();
        }
        
        // org.kframework.kil.Definition javaDef;
        // sw.start();
        // javaDef = defLoader.get().loadDefinition(options.mainDefinitionFile(), options.mainModule(),
        //         context);

        // loader.saveOrDie(files.resolveKompiled("definition-concrete.bin"), javaDef);

        // Backend b = backend.get();
        // CompilerSteps<Definition> steps = b.getCompilationSteps();

        // if (step == null) {
        //     step = b.getDefaultStep();
        // }
        // try {
        //     javaDef = steps.compile(javaDef, step);
        // } catch (CompilerStepDone e) {
        //     javaDef = (Definition) e.getResult();
        // }

        // loader.saveOrDie(files.resolveKompiled("configuration.bin"),
        //         MetaK.getConfiguration(javaDef, context));

        // b.run(javaDef);

        

        return CompiledDefinition(options, parsedDef, kompiledDef, programStartSymbol, topCellInitializer);
    }
    
    @Test
    public void testOcaml() {
        KExceptionManager kem = new KExceptionManager(new GlobalOptions());
        String kfile = "/home/remy/Documents/ResearchWork/KHaskell/k/kernel/src/test/resources/kore_imp_tiny.k";
        CompiledDefinition def = kompile(kem, new File(kfile));

        BiFunction<String, Source, K> programParser = def.getProgramParser(kem);

        K program = programParser.apply(
                "int s, n, .Ids; n = 10; while(0<=n) { s = s + n; n = n + -1; }", Source.apply("generated by DefinitionToOcaml"));

        DefinitionToOcaml convert = new DefinitionToOcaml();
        String ocaml = convert.convert(def);
        System.out.println(ocaml);
        String pgm = convert.convert(new KRun(kem, FileUtil.testFileUtil()).plugConfigVars(def, Collections.singletonMap(KToken(Sorts.KConfigVar(), "$PGM"), program)));
        System.out.println(pgm);

        assertEquals(true, true);
    }
    
    // @Mock
    // private Context context;

    // private ToKAppTransformer transformer;

    // private DataStructureSort mapSort;
    // private DataStructureSort listSort;
    // private DataStructureSort setSort;

    // @Before
    // public void setUp() {
    //     transformer = new ToKAppTransformer(context, new Comparator<Term>() {

    //         @Override
    //         public int compare(Term o1, Term o2) {
    //             return o1.toString().compareTo(o2.toString());
    //         }
    //     });
    //     mapSort = new DataStructureSort("Map", Sort.MAP, "'_Map_", "'_|->_", "'.Map", Maps.<String, String>newHashMap());
    //     listSort = new DataStructureSort("List", Sort.LIST, "'_List_", "'ListItem", "'.List", Maps.<String, String>newHashMap());
    //     setSort = new DataStructureSort("Set", Sort.SET, "'_Set_", "'SetItem", "'.Set", Maps.<String, String>newHashMap());
    //     when(context.dataStructureSortOf(Sort.MAP)).thenReturn(mapSort);
    //     when(context.dataStructureSortOf(Sort.LIST)).thenReturn(listSort);
    //     when(context.dataStructureSortOf(Sort.SET)).thenReturn(setSort);
    // }

    // @Test
    // public void testUnit() {
    //     MapBuiltin mapUnit = (MapBuiltin) DataStructureBuiltin.empty(mapSort);
    //     ListBuiltin listUnit = (ListBuiltin) DataStructureBuiltin.empty(listSort);
    //     SetBuiltin setUnit = (SetBuiltin) DataStructureBuiltin.empty(setSort);

    //     KApp mapKApp = (KApp) transformer.visitNode(mapUnit);
    //     KApp listKApp = (KApp) transformer.visitNode(listUnit);
    //     KApp setKApp = (KApp) transformer.visitNode(setUnit);

    //     assertEquals(KApp.of("'.Map"), mapKApp);
    //     assertEquals(KApp.of("'.List"), listKApp);
    //     assertEquals(KApp.of("'.Set"), setKApp);
    // }

    // @Test
    // public void testElement() {
    //     MapBuiltin mapElement = (MapBuiltin) DataStructureBuiltin.element(mapSort, KSequence.EMPTY, KSequence.EMPTY);
    //     ListBuiltin listElement = (ListBuiltin) DataStructureBuiltin.element(listSort, KSequence.EMPTY);
    //     SetBuiltin setElement = (SetBuiltin) DataStructureBuiltin.element(setSort, KSequence.EMPTY);

    //     KApp mapKApp = (KApp) transformer.visitNode(mapElement);
    //     KApp listKApp = (KApp) transformer.visitNode(listElement);
    //     KApp setKApp = (KApp) transformer.visitNode(setElement);

    //     assertEquals(KApp.of("'_|->_", KSequence.EMPTY, KSequence.EMPTY), mapKApp);
    //     assertEquals(KApp.of("'ListItem", KSequence.EMPTY), listKApp);
    //     assertEquals(KApp.of("'SetItem", KSequence.EMPTY), setKApp);
    // }

    // @Test
    // public void testConstructor() {
    //     MapBuiltin mapElement1 = (MapBuiltin) DataStructureBuiltin.element(mapSort, IntBuiltin.kAppOf(1), KSequence.EMPTY);
    //     MapBuiltin mapElement2 = (MapBuiltin) DataStructureBuiltin.element(mapSort, IntBuiltin.kAppOf(2), KSequence.EMPTY);
    //     ListBuiltin listElement = (ListBuiltin) DataStructureBuiltin.element(listSort, KSequence.EMPTY);
    //     SetBuiltin setElement1 = (SetBuiltin) DataStructureBuiltin.element(setSort, IntBuiltin.kAppOf(1));
    //     SetBuiltin setElement2 = (SetBuiltin) DataStructureBuiltin.element(setSort, IntBuiltin.kAppOf(2));

    //     MapBuiltin map = (MapBuiltin) DataStructureBuiltin.of(mapSort, mapElement1, mapElement2);
    //     ListBuiltin list = (ListBuiltin) DataStructureBuiltin.of(listSort, listElement, listElement);
    //     SetBuiltin set = (SetBuiltin) DataStructureBuiltin.of(setSort, setElement1, setElement2);

    //     KApp mapKApp = (KApp) transformer.visitNode(map);
    //     KApp listKApp = (KApp) transformer.visitNode(list);
    //     KApp setKApp = (KApp) transformer.visitNode(set);

    //     assertEquals(KApp.of("'_Map_", KApp.of("'_|->_", IntBuiltin.kAppOf(1), KSequence.EMPTY),
    //             KApp.of("'_|->_", IntBuiltin.kAppOf(2), KSequence.EMPTY)), mapKApp);
    //     assertEquals(KApp.of("'_List_", KApp.of("'ListItem", KSequence.EMPTY),
    //             KApp.of("'ListItem", KSequence.EMPTY)), listKApp);
    //     assertEquals(KApp.of("'_Set_", KApp.of("'SetItem", IntBuiltin.kAppOf(1)),
    //             KApp.of("'SetItem", IntBuiltin.kAppOf(2))), setKApp);

    // }

    // @Test
    // public void testFrame() {
    //     MapBuiltin mapElement = (MapBuiltin) DataStructureBuiltin.element(mapSort, KSequence.EMPTY, KSequence.EMPTY);
    //     ListBuiltin listElement = (ListBuiltin) DataStructureBuiltin.element(listSort, KSequence.EMPTY);
    //     SetBuiltin setElement = (SetBuiltin) DataStructureBuiltin.element(setSort, KSequence.EMPTY);

    //     MapBuiltin map = (MapBuiltin) DataStructureBuiltin.of(mapSort, mapElement, new Variable("M", Sort.MAP));
    //     ListBuiltin list = (ListBuiltin) DataStructureBuiltin.of(listSort, listElement, new Variable("L", Sort.LIST));
    //     SetBuiltin set = (SetBuiltin) DataStructureBuiltin.of(setSort, setElement, new Variable("S", Sort.SET));

    //     KApp mapKApp = (KApp) transformer.visitNode(map);
    //     KApp listKApp = (KApp) transformer.visitNode(list);
    //     KApp setKApp = (KApp) transformer.visitNode(set);

    //     assertEquals(KApp.of("'_Map_", KApp.of("'_|->_", KSequence.EMPTY, KSequence.EMPTY),
    //             new Variable("M", Sort.MAP)), mapKApp);
    //     assertEquals(KApp.of("'_List_", KApp.of("'ListItem", KSequence.EMPTY),
    //             new Variable("L", Sort.LIST)), listKApp);
    //     assertEquals(KApp.of("'_Set_", KApp.of("'SetItem", KSequence.EMPTY),
    //             new Variable("S", Sort.SET)), setKApp);
    // }
}
