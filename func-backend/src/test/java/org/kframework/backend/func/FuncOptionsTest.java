// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.beust.jcommander.JCommander;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.kframework.backend.func.FuncUtil.*;
import static org.mockito.Mockito.*;

/**
 * Test the {@link FuncOptions} class
 */
@RunWith(MockitoJUnitRunner.class)
public class FuncOptionsTest {
    @Test
    public void testOptLevel() {
        Optional<FuncOptions.OptLevel> slow =
            Optional.of(FuncOptions.OptLevel.SLOW);
        Optional<FuncOptions.OptLevel> fast =
            Optional.of(FuncOptions.OptLevel.FAST);
        Optional<FuncOptions.OptLevel> invalid =
            Optional.empty();

        Function<FuncOptions, Optional<FuncOptions.OptLevel>> getter =
            x -> x.getOptLevel();

        String opt1 = "-O";
        testJC(getter, slow,    opt1, "0");
        testJC(getter, fast,    opt1, "1");
        testJC(getter, fast,    opt1, "2");
        testJC(getter, fast,    opt1, "3");
        testJC(getter, invalid, opt1, "invalid");
        testJC(getter, invalid, opt1, "");
        String opt2 = "--optimization";
        testJC(getter, slow,    opt2, "0");
        testJC(getter, fast,    opt2, "1");
        testJC(getter, fast,    opt2, "2");
        testJC(getter, fast,    opt2, "3");
        testJC(getter, invalid, opt2, "invalid");
        testJC(getter, invalid, opt2, "");
    }

    @Test
    public void testGenMLOnly() {
        Function<FuncOptions, Boolean> getter = x -> x.getGenMLOnly();
        testJC(getter, false, "");
        testJC(getter, true,  "--gen-ml-only");
    }

    @Test
    public void testPackages() {
        Function<FuncOptions, List<String>> getter = x -> x.getPackages();
        String opt = "--packages";
        testJC(getter, asList("a"),           opt, "a");
        testJC(getter, asList("a", "b"),      opt, "a b");
        testJC(getter, asList("a", "b", "c"), opt, "a b c");
    }

    @Test
    public void testHookNamespaces() {
        Function<FuncOptions, List<String>> getter = x -> x.getHookNamespaces();
        String opt = "--hook-namespaces";
        testJC(getter, asList("a"),           opt, "a");
        testJC(getter, asList("a", "b"),      opt, "a b");
        testJC(getter, asList("a", "b", "c"), opt, "a b c");
    }

    @Test
    public void testNoLinkPrelude() {
        Function<FuncOptions, Boolean> getter = x -> x.getNoLinkPrelude();
        testJC(getter, false, "");
        testJC(getter, true,  "--no-link-prelude");
    }

    private <A, B extends A> void testJC(Function<FuncOptions, B> getter,
                                         A expected,
                                         String... args) {
        FuncOptions fo = new FuncOptions();
        new JCommander(fo, args);
        Assert.assertEquals(expected, getter.apply(fo));
    }
}
