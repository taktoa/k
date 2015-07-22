// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Arrays;
import java.util.Collection;

import java.util.stream.Collector;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.HashMap;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.io.FileUtils;

import org.kframework.kore.K;
import org.kframework.definition.Sentence;
import org.kframework.parser.ProductionReference;
import org.kframework.attributes.Source;
import org.kframework.attributes.Location;

import org.kframework.utils.file.FileUtil;
import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.utils.errorsystem.KEMException.*;

/**
 * Utility functions for use in
 * other parts of the functional backend
 *
 * @author Remy Goldschmidt
 */
public final class FuncUtil {
    private FuncUtil() {}


    // ------------------------------------------------------------------------
    // ------------------------------- XML debug ------------------------------
    // ------------------------------------------------------------------------


    public static XMLBuilder newxml() {
        return new XMLBuilder();
    }

    public static XMLBuilder.XMLAttr[] emptyXMLAttrs() {
        return new XMLBuilder.XMLAttr[] {};
    }

    public static XMLBuilder.XMLAttr xmlAttr(Object name, Object value) {
        return new XMLBuilder.XMLAttr(escapeXML(name.toString()),
                                      escapeXML(value.toString()));
    }

    public static XMLBuilder.XMLAttr[] xmlAttrs(Object... objs) {
        if(objs.length % 2 != 0) {
            throw new AssertionError("xmlAttrs needs an even number of args");
        }

        XMLBuilder.XMLAttr[] res = new XMLBuilder.XMLAttr[objs.length / 2];
        for(int i = 0; i + 1 < objs.length; i += 2) {
            res[i / 2] = xmlAttr(objs[i], objs[i + 1]);
        }

        return res;
    }

    public static String escapeXML(String str) {
        return StringEscapeUtils.escapeXml(str);
    }

    public static String xmlToSExpr(FileUtil files, String xml) {
        String inName  = "input.xml";
        String outName = "output.scm";

        File scriptDir = files.resolveKBase("include/func");
        File input  = files.resolveTemp(inName);
        File output = files.resolveTemp(outName);

        String[] cmd = new String[] { "guile",
                                      "--fresh-auto-compile",
                                      "-s",
                                      "normalize.scm",
                                      input.getAbsolutePath() };


        String result = "";

        synchronized(files) {
            try {
                files.saveToTemp(inName, xml);

                Process p =
                    files
                    .getProcessBuilder()
                    .directory(scriptDir)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(output)
                    .command(cmd)
                    .start();

                int exit = p.waitFor();

                result = files.load(output);

                FileUtils.forceDelete(input);
                FileUtils.forceDelete(output);

                if(exit != 0) {
                    outprintf("%s", result);
                    String fmt = "Guile returned exit code: %d";
                    throw new Exception(String.format(fmt, exit));
                }
            } catch(Exception e) {
                throw xmlToSExprException(e);
            }
        }

        return result;
    }

    private static KEMException xmlToSExprException(Exception e) {
        return kemCriticalErrorF("%s\n%s\n%s",
                                 "Error converting xml to s-expression.",
                                 "Do you have GNU Guile installed?", e);
    }

    // ------------------------------------------------------------------------
    // --------------------------- Numeric functions --------------------------
    // ------------------------------------------------------------------------


    public static boolean between(int val, int min, int max) {
        return val > min && max > val;
    }


    // ------------------------------------------------------------------------
    // ----------------------- SyntaxBuilder generators -----------------------
    // ------------------------------------------------------------------------


    public static SyntaxBuilder newsb() {
        return new SyntaxBuilder();
    }

    public static SyntaxBuilder newsb(String s) {
        return new SyntaxBuilder(s);
    }

    public static SyntaxBuilder newsb(SyntaxBuilder sb) {
        return new SyntaxBuilder(sb);
    }

    public static SyntaxBuilder newsb(String... strings) {
        return new SyntaxBuilder(strings);
    }

    public static SyntaxBuilder newsbf(String fmt, Object... args) {
        return newsb().appendf(fmt, args);
    }

    public static SyntaxBuilder newsbv(String value) {
        return newsb().addValue(value);
    }

    public static SyntaxBuilder newsbk(String keyword) {
        return newsb().addKeyword(keyword);
    }


    // ------------------------------------------------------------------------
    // ------------------------ Collection generators -------------------------
    // ------------------------------------------------------------------------


    /**
     * Since we are generally statically importing FuncUtil,
     * it is nice to eliminate the Lists.newArrayList pattern
     */
    public static <E> ArrayList<E> newArrayList() {
        return Lists.newArrayList();
    }

    /**
     * Since, newArrayListWithCapacity is an awfully long name, we
     * overload newArrayList to have an integer argument
     * It shouldn't be confusing given that there are no other
     * overloadings of Lists.newArrayList
     */
    public static <E> ArrayList<E> newArrayList(int capacity) {
        return newArrayListWithCapacity(capacity);
    }

    /**
     * Since we are generally statically importing FuncUtil,
     * it is nice to eliminate the Lists.newArrayListWithCapacity pattern
     */
    public static <E> ArrayList<E> newArrayListWithCapacity(int capacity) {
        return Lists.newArrayListWithCapacity(capacity);
    }

    /**
     * Since we are generally statically importing FuncUtil,
     * it is nice to eliminate the Lists.newLinkedList pattern
     */
    public static <E> LinkedList<E> newLinkedList() {
        return Lists.newLinkedList();
    }

    /**
     * Since we are generally statically importing FuncUtil,
     * it is nice to eliminate the Sets.newHashSet() pattern
     */
    public static <E> HashSet<E> newHashSet() {
        return Sets.newHashSet();
    }

    /**
     * Since we are generally statically importing FuncUtil,
     * it is nice to eliminate the Maps.newHashMap() pattern
     */
    public static <K, V> HashMap<K, V> newHashMap() {
        return Maps.newHashMap();
    }


    // ------------------------------------------------------------------------
    // -------------------------- Stream collectors ---------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the Collectors.toList pattern */
    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.toList();
    }

    /** Eliminates the Collectors.toSet pattern */
    public static <T> Collector<T, ?, Set<T>> toSet() {
        return Collectors.toSet();
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joining() {
        return Collectors.joining();
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joining(String del) {
        return Collectors.joining(del);
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joining(String del,
                                                             String pfx,
                                                             String sfx) {
        return Collectors.joining(del, pfx, sfx);
    }


    // ------------------------------------------------------------------------
    // ---------------------- Process-related functions -----------------------
    // ------------------------------------------------------------------------


    /**
     * Start a process with the given parameters
     * @param pb    A {@link ProcessBuilder} to use
     * @param dir   A directory in which to start the process
     * @param err   A file to send stderr to
     * @param out   A file to send stdout to
     * @param cmd   The command to run
     * @return      A {@link Process process} to wait on
     */
    public static Process startProcess(ProcessBuilder pb,
                                       File dir,
                                       File err,
                                       File out,
                                       String... cmd) throws IOException {
        return pb.command(cmd)
                 .directory(dir)
                 .redirectError(err)
                 .redirectOutput(out)
                 .start();
    }

    /**
     * Start a process with the given parameters (does not redirect stdout/stderr)
     * @param pb    A {@link ProcessBuilder} to use
     * @param dir   A directory in which to start the process
     * @param cmd   The command to run
     * @return      A {@link Process process} to wait on
     */
    public static Process startProcess(ProcessBuilder pb,
                                       File dir,
                                       String... cmd) throws IOException {
        return pb.command(cmd)
                 .directory(dir)
                 .inheritIO()
                 .start();
    }


    // ------------------------------------------------------------------------
    // ---------------------------- List functions ----------------------------
    // ------------------------------------------------------------------------


    /**
     * Eliminates the Arrays.asList pattern
     */
    public static <T> List<T> asList(T... elements) {
        return Arrays.asList(elements);
    }

    /**
     * Add all the elements of an array {@code A[]} to a given
     * {@code ? extends Collection<T>} where {@code A extends T}.
     * <br>
     * Any underlying ordering to the given collection will be respected,
     * assuming that that collection implements {@link Collection#addAll addAll}
     * in a way that respects ordering.
     * <br>
     * Should run in <code>O(m + n)</code> time and <code>O(m + n)</code> space,
     * where <code>m = col.size()</code> and <code>n = arr.length</code>
     * Should be thread-safe (it synchronizes on {@code col} and {@code arr}).
     */
     public static <T, C extends Collection<T>, A extends T> C addMany(C col,
                                                                       A... arr) {
        List<T> al = Lists.newArrayListWithCapacity(col.size() + arr.length + 1);

        synchronized(col) {
            al.addAll(col);
        }

        synchronized(arr) {
            for(int i = 0; i < arr.length; i++) {
                al.add(arr[i]);
            }
        }

        return (C) al;
    }

    /**
     * Outputs an ArrayList-backed List of Integers that represents a
     * closed interval from {@code start} to {@code stop} by {@code step}
     * <br><br>
     * For some <code> r = rangeInclusive(a, &Delta;, b) </code>
     * and <code> s = r.size() </code>
     * <style type='text/css'> .eqn { margin-bottom: 20px; } </style>
     * <ol>
     * <li class="eqn"><pre> s            === 1 + ((b - a) / &Delta;) </pre></li>
     * <li class="eqn"><pre> r.get(0)     === a                       </pre></li>
     * <li class="eqn"><pre> r.get(i)     === (i * &Delta;) + a       </pre></li>
     * <li class="eqn"><pre> r.get(s - 1) === b                       </pre></li>
     * </ol>
     * @param start start of the range
     * @param step  difference between any two consecutive elements
     * @param stop  end of the range
     */
    public static List<Integer> rangeInclusive(int start, int step, int stop) {
        int elements = Math.abs((stop - start) / step);
        int padding = 4;
        List<Integer> result = Lists.newArrayList(elements + padding);
        for(int i = start; i <= stop; i += step) {
            result.add(Integer.valueOf(i));
        }
        return result;
    }

    /**
     * Same as {@link rangeInclusive(int, int, int) rangeInclusive},
     * except it excludes the first and last element
     */
    public static List<Integer> rangeExclusive(int start, int step, int stop) {
        List<Integer> result = rangeInclusive(start, step, stop);
        result.remove(0);
        result.remove(result.size() - 1);
        return result;
    }


    /**
     * Same as {@link rangeInclusive(int, int, int) rangeInclusive},
     * except it defaults the step to 1
     */
    public static List<Integer> rangeInclusive(int start, int stop) {
        return rangeInclusive(start, 1, stop);
    }

    /**
     * Same as {@link rangeInclusive(int, int) rangeInclusive},
     * except it defaults the start to 0
     */
    public static List<Integer> rangeInclusive(int stop) {
        return rangeInclusive(0, stop);
    }

    /**
     * Same as {@link rangeExclusive(int, int, int) rangeExclusive},
     * except it defaults the step to 1
     */
    public static List<Integer> rangeExclusive(int start, int stop) {
        return rangeExclusive(start, 1, stop);
    }

    /**
     * Same as {@link rangeExclusive(int, int) rangeExclusive},
     * except it defaults the start to 0
     */
    public static List<Integer> rangeExclusive(int stop) {
        return rangeExclusive(0, stop);
    }


    // ------------------------------------------------------------------------
    // ------------------------- System.out.* aliases -------------------------
    // ------------------------------------------------------------------------


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


    // ------------------------------------------------------------------------
    // ------------------------- KEMException aliases -------------------------
    // ------------------------------------------------------------------------


    // ------------------ KEMException.compilerError aliases ------------------

    /**
     * Helper function for
     * {@link compilerError(String) compilerError}
     */
    public static KEMException kemCompilerErrorF(String fmt,
                                                 Object... obj) {
        return compilerError(String.format(fmt, obj));
    }

    /**
     * Helper function for
     * {@link compilerError(String, Throwable) compilerError}
     */
    public static KEMException kemCompilerErrorF(Throwable e,
                                                 String fmt,
                                                 Object... obj) {
        return compilerError(String.format(fmt, obj), e);
    }

    /**
     * Helper function for
     * {@link compilerError(String, K) compilerError}
     */
    public static KEMException kemCompilerErrorF(K node,
                                                 String fmt,
                                                 Object... obj) {
        return compilerError(String.format(fmt, obj), node);
    }

    /**
     * Helper function for
     * {@link compilerError(String, Sentence) compilerError}
     */
    public static KEMException kemCompilerErrorF(Sentence node,
                                                 String fmt,
                                                 Object... obj) {
        return compilerError(String.format(fmt, obj), node);
    }

    // ------------------ criticalError aliases ------------------

    /**
     * Helper function for
     * {@link criticalError(String) criticalError}
     */
    public static KEMException kemCriticalErrorF(String fmt,
                                                 Object... obj) {
        return criticalError(String.format(fmt, obj));
    }

    /**
     * Helper function for
     * {@link criticalError(String, Throwable) criticalError}
     */
    public static KEMException kemCriticalErrorF(Throwable e,
                                                 String fmt,
                                                 Object... obj) {
        return criticalError(String.format(fmt, obj), e);
    }

    /**
     * Helper function for
     * {@link criticalError(String, K) criticalError}
     */
    public static KEMException kemCriticalErrorF(K node,
                                                 String fmt,
                                                 Object... obj) {
        return criticalError(String.format(fmt, obj), node);
    }

    /**
     * Helper function for
     * {@link criticalError(String, Sentence) criticalError}
     */
    public static KEMException kemCriticalErrorF(Sentence node,
                                                 String fmt,
                                                 Object... obj) {
        return criticalError(String.format(fmt, obj), node);
    }

    /**
     * Helper function for
     * {@link criticalError(String, ProductionReference) criticalError}
     */
    public static KEMException kemCriticalErrorF(ProductionReference node,
                                                 String fmt,
                                                 Object... obj) {
       return criticalError(String.format(fmt, obj), node);
    }

    /**
     * Helper function for
     * {@link criticalError(String, Throwable, Location, Source) criticalError}
     */
    public static KEMException kemCriticalErrorF(Throwable e,
                                                 Location loc,
                                                 Source src,
                                                 String fmt,
                                                 Object... obj) {
        return criticalError(String.format(fmt, obj), e, loc, src);
    }

    // ----------------- innerParserError aliases ----------------

    /**
     * Helper function for
     * {@link innerParserError(String) innerParserError}
     */
    public static KEMException kemInnerParserErrorF(String fmt,
                                                    Object... obj) {
        return innerParserError(String.format(fmt, obj));
    }

    /**
     * Helper function for
     * {@link innerParserError(String, Throwable, Location, Source) innerParserError}
     */
    public static KEMException kemInnerParserErrorF(Throwable e,
                                                    Location loc,
                                                    Source src,
                                                    String fmt,
                                                    Object... obj) {
        return innerParserError(String.format(fmt, obj), e, src, loc);
    }

    // ------------------ internalError aliases ------------------

    /**
     * Helper function for
     * {@link internalError(String) internalError}
     */
    public static KEMException kemInternalErrorF(String fmt,
                                                 Object... obj) {
        return internalError(String.format(fmt, obj));
    }

    /**
     * Helper function for
     * {@link internalError(String, Throwable) internalError}
     */
    public static KEMException kemInternalErrorF(Throwable e,
                                                 String fmt,
                                                 Object... obj) {
        return internalError(String.format(fmt, obj), e);
    }

    /**
     * Helper function for
     * {@link internalError(String, K) internalError}
     */
    public static KEMException kemInternalErrorF(K node,
                                                 String fmt,
                                                 Object... obj) {
        return internalError(String.format(fmt, obj), node);
    }

    // ----------------- outerParserError aliases ----------------

    /**
     * Helper function for
     * {@link outerParserError(String, Throwable, Location, Source) outerParserError}
     */
    public static KEMException kemOuterParserErrorF(Throwable e,
                                                    Location loc,
                                                    Source src,
                                                    String fmt,
                                                    Object... obj) {
        return outerParserError(String.format(fmt, obj), e, src, loc);
    }
}
