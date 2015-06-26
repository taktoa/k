// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import java.util.List;
import java.util.Collection;
import com.google.common.collect.Lists;

/**
 * Utility functions for use in
 * other parts of the functional backend
 * @author: Remy Goldschmidt
 */
public final class FuncUtil {
    private FuncUtil() {}

    public static <T, C extends Collection<T>, A extends T> void addArray(C col, A[] arr) {
        for(int i = 0; i < arr.length; i++) {
            col.add(arr[i]);
        }
    }

    /**
     * An alias so we don't have System.out.* everywhere
     * @param fmt   the format string to pass to System.out.format
     * @param args  the Objects to pass to System.out.format
     */
    public static void outprintf(String fmt, Object... args) {
        System.out.format(fmt, args);
    }

    /**
     * This is just outprintf with a newline added to
     * the end of the format string
     * @param fmt   the format string to pass to System.out.format
     * @param args  the Objects to pass to System.out.format
     */
    public static void outprintfln(String fmt, Object... args) {
        outprintf(fmt + "\n", args);
    }

    /**
     * In case you misspell it, we have this version, so it will give
     * a deprecation warning at compile time.
     * @deprecated use {@link #outprintfln()} instead
     * @param fmt   the format string to pass to System.out.format
     * @param args  the Objects to pass to System.out.format
     */
    @Deprecated
    public static void outprintlnf(String fmt, Object... args) {
        outprintfln(fmt, args);
    }

    /**
     * An alias so we don't have System.err.* everywhere
     * @param fmt   the format string to pass to System.err.format
     * @param args  the Objects to pass to System.err.format
     */
    public static void errprintf(String fmt, Object... args) {
        System.err.format(fmt, args);
    }

    /**
     * This is just errprintf with a newline added to
     * the end of the format string
     * @param fmt   the format string to pass to System.err.format
     * @param args  the Objects to pass to System.err.format
     */
    public static void errprintfln(String fmt, Object... args) {
        errprintf(fmt + "\n", args);
    }

    /**
     * In case you misspell it, we have this version, so it will give
     * a deprecation warning at compile time.
     * @deprecated  use {@link #errprintfln()} instead
     * @param fmt   the format string to pass to System.err.format
     * @param args  the Objects to pass to System.err.format
     */
    @Deprecated
    public static void errprintlnf(String fmt, Object... args) {
        errprintfln(fmt, args);
    }

    /**
     * Outputs an ArrayList-backed List of Integers
     */
    public static List<Integer> rangeInclusive(int min, int step, int max) {
        int elements = Math.abs((max - min) / step);
        int padding = 4;
        List<Integer> result = Lists.newArrayList(elements + padding);
        for(int i = min; i <= max; i += step) {
            result.add(new Integer(i));
        }
        return result;
    }

    public static List<Integer> rangeExclusive(int min, int step, int max) {
        List<Integer> result = rangeInclusive(min, step, max);
        result.remove(0);
        result.remove(result.size() - 1);
        return result;
    }

    public static List<Integer> rangeInclusive(int min, int max) {
        return rangeInclusive(min, 1, max);
    }

    public static List<Integer> rangeInclusive(int max) {
        return rangeInclusive(0, max);
    }

    public static List<Integer> rangeExclusive(int min, int max) {
        return rangeExclusive(min, 1, max);
    }

    public static List<Integer> rangeExclusive(int max) {
        return rangeExclusive(0, max);
    }
}
