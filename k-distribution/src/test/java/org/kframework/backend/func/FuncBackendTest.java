// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Comparator;

import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.kframework.main.GlobalOptions;
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

import org.kframework.main.Main;

import org.apache.commons.io.FileUtils;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class FuncBackendTest {
    private static final String tempDirPrefix = "k-func-tmp-";

    private File kfile;
    private File kdir;
    private String[] kompArgs;

    // Create a temp directory, copy the given file to it.
    // Also, register a shutdown hook for deleting the temp dir.
    private void kdefCreateTmp(File kdef) throws IOException {
        kdir = Files.createTempDirectory(tempDirPrefix).toFile();
        kfile = new File(kdir.getPath() + File.separator + kdef.getName());
        Files.copy(kdef.toPath(), kfile.toPath());
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        FileUtils.deleteDirectory(kdir);
                    } catch(IOException e) {
                        System.out.println("Failed to delete tmp dir: " + kdir);
                        System.out.println(e);
                    }
                }
            });
    }

    // Initialize all the member variables
    private void testInit() throws IOException {
        kdefCreateTmp(new File("src/test/resources/calc.k"));
        // testPgm = "int n, .Ids; n = 10; while(0 <= n) { n = n + -1; }";
        kompArgs = new String[] { "-kompile"
                                , "--backend"
                                , "func"
                                , "-d"
                                , kdir.getAbsolutePath()
                                , "-v"
                                , "--debug"
                                , kfile.getAbsolutePath()
                                };
    }

    @Test
    public void testFuncBackend() throws IOException {
        testInit();
        Main.main(kompArgs);
        assertEquals(true, false); // TODO(taktoa): replace with actual unit test.
    }
}
