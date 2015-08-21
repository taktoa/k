// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;

import java.util.stream.Collector;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.HashMap;

import java.util.function.Function;
import java.util.function.Predicate;

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
 * Utility functions for use in other parts of the functional backend
 *
 * @author Remy Goldschmidt
 */
public final class FuncUtil {
    // ------------------------------------------------------------------------
    // -------------------------------- Fields --------------------------------
    // ------------------------------------------------------------------------


    private static final String nl = String.format("%n");


    // ------------------------------------------------------------------------
    // ------------------------------ Constructor -----------------------------
    // ------------------------------------------------------------------------


    private FuncUtil() {}


    // ------------------------------------------------------------------------
    // ---------------------------- Misc functions ----------------------------
    // ------------------------------------------------------------------------


    /** Return a system-dependent newline String. */
    public static String newline() {
        return nl;
    }


    // ------------------------------------------------------------------------
    // ---------------------------- XML functions -----------------------------
    // ------------------------------------------------------------------------


    /** Create a new XMLBuilder */
    public static XMLBuilder newxml() {
        return new XMLBuilder();
    }

    /** An empty array of XMLAttrs, for convenience */
    public static XMLBuilder.XMLAttr[] emptyXMLAttrs() {
        return new XMLBuilder.XMLAttr[] {};
    }

    /** Create an XMLAttr from the given name and value */
    public static XMLBuilder.XMLAttr xmlAttr(Object name, Object value) {
        return new XMLBuilder.XMLAttr(escapeXML(name.toString()),
                                      escapeXML(value.toString()));
    }

    /** Create a list of XMLAttrs from the given objects, which are paired. */
    public static List<XMLBuilder.XMLAttr> xmlAttrsL(Object... objs) {
        List<XMLBuilder.XMLAttr> res = newArrayList(3 + objs.length / 2);
        for(Pair<Object, Object> pair : toPairsA(objs)) {
            res.add(xmlAttr(pair.getLeft(), pair.getRight()));
        }
        return res;
    }

    /** Create an array of XMLAttrs from the given objects, which are paired. */
    public static XMLBuilder.XMLAttr[] xmlAttrs(Object... objs) {
        List<XMLBuilder.XMLAttr> resL = xmlAttrsL(objs);
        XMLBuilder.XMLAttr[] res = new XMLBuilder.XMLAttr[resL.size()];
        for(int i = 0; i < resL.size(); i++) { res[i] = resL.get(i); }
        return res;
    }

    /** Eliminates the StringEscapeUtils.escapeXML pattern. */
    public static String escapeXML(String str) {
        return StringEscapeUtils.escapeXml(str);
    }

    /** Convert the given XML to an s-expression with a Guile script. */
    public static String xmlToSExpr(XMLBuilder xml) {
        return xmlToSExpr(xml.toString());
    }

    /** Convert the given XML to an s-expression with a Guile script. */
    public static String xmlToSExpr(String xml) {
        FileUtil files = FileUtil.testFileUtil();

        String inName  = "input.xml";
        String outName = "output.scm";

        File scriptDir = files.resolveKBase("include/func");
        File input     = files.resolveTemp(inName);
        File output    = files.resolveTemp(outName);

        String[] cmd = new String[] {  };

        String result = "";

        try {
            files.save(input, xml);

            Process p = startProcess(files.getProcessBuilder(),
                                     scriptDir,
                                     ProcessBuilder.Redirect.INHERIT,
                                     ProcessBuilder.Redirect.to(output),
                                     "guile", "--fresh-auto-compile", "-s",
                                     "normalize.scm",
                                     input.getAbsolutePath());

            int exit = p.waitFor();

            FileUtils.forceDelete(input);
            result = files.load(output);
            FileUtils.forceDelete(output);

            if(exit != 0) {
                files.save(output, result);
                String fmt = "Guile returned exit code: %d";
                throw new Exception(String.format(fmt, exit));
            }
        } catch(Exception e) {
            throw xmlToSExprException(e);
        }

        return result;
    }

    private static KEMException xmlToSExprException(Exception e) {
        return kemCriticalErrorF("%s\n%s\n%s",
                                 "Error converting xml to s-expression.",
                                 "Do you have GNU Guile installed?", e);
    }


    // ------------------------------------------------------------------------
    // --------------------------- String functions ---------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the String.format pattern */
    public static String fmt(String fmt, Object... objs) {
        return String.format(fmt, objs);
    }


    // ------------------------------------------------------------------------
    // --------------------------- Numeric functions --------------------------
    // ------------------------------------------------------------------------


    /**
     * Is the given value within the given range?
     * @param val   Value to test
     * @param min   Low end of the range
     * @param max   High end of the range
     * @return      Whether or not the value is within the range
     */
    public static boolean between(int val, int min, int max) {
        return val > min && max > val;
    }


    // ------------------------------------------------------------------------
    // ------------------------ SyntaxBuilder functions -----------------------
    // ------------------------------------------------------------------------


    /** Create a new SB */
    public static SyntaxBuilder newsb() {
        return new SyntaxBuilder();
    }

    /** Create a new SB containing the given string */
    public static SyntaxBuilder newsb(String string) {
        return new SyntaxBuilder(string);
    }

    /** Create a new SB containing the given strings */
    public static SyntaxBuilder newsb(String... strings) {
        return new SyntaxBuilder(strings);
    }

    /** Create a new SB containing the given SyntaxBuilder */
    public static SyntaxBuilder newsb(SyntaxBuilder sb) {
        return new SyntaxBuilder(sb);
    }

    /** Create a new SB containing a formatted string */
    public static SyntaxBuilder newsbf(String fmt, Object... args) {
        return newsb().appendf(fmt, args);
    }

    /** Create a new SB containing the given string, annotated as a value. */
    public static SyntaxBuilder newsbv(String value) {
        return newsb().addValue(newsb(value));
    }

    /** Create a new SB containing the given string, annotated as a keyword. */
    public static SyntaxBuilder newsbk(String keyword) {
        return newsb().addKeyword(newsb(keyword));
    }

    /** Create a new SB containing the given string, annotated as a name. */
    public static SyntaxBuilder newsbn(String name) {
        return newsb().addName(name);
    }

    /** Create a new SB containing the given string, annotated as a pattern. */
    public static SyntaxBuilder newsbp(String pattern) {
        return newsb().addPattern(newsb(pattern));
    }

    /** Create a new SB containing the given integer. */
    public static SyntaxBuilder newsbInt(int integer) {
        return newsb().addInteger(integer);
    }

    /** Create a new SB containing the given float. */
    public static SyntaxBuilder newsbFlt(float flt) {
        return newsb().addFloat(flt);
    }

    /** Create a new SB containing the given string as a language string. */
    public static SyntaxBuilder newsbStr(String string) {
        return newsb().addString(string);
    }

    /** Create a new SB containing the given boolean. */
    public static SyntaxBuilder newsbBool(boolean bool) {
        return newsb().addBoolean(bool);
    }

    /** Create a new SB containing a function applied to arguments. */
    public static SyntaxBuilder newsbApp(String f, SyntaxBuilder... as) {
        return newsb().addApplication(f, as);
    }

    /** Create a new SB containing a sequence of actions. */
    public static SyntaxBuilder newsbSeq(SyntaxBuilder... items) {
        return newsb().addSequence(items);
    }

    /** Create a new SB containing a tuple of values. */
    public static SyntaxBuilder newsbTup(SyntaxBuilder... items) {
        return newsb().addTuple(items);
    }

    /** Create a new SB containing a list of values. */
    public static SyntaxBuilder newsbList(SyntaxBuilder... items) {
        return newsb().addList(items);
    }


    // ------------------------------------------------------------------------
    // --------------------- Generic collection functions ---------------------
    // ------------------------------------------------------------------------


    /** Map a function over a collection and return a List. */
    public static <E1, E2,
                   C extends Collection<E1>,
                   F extends Function<E1, E2>> List<E2> mapCollL(F f, C c) {
        return c.stream().map(f).collect(toListC());
    }

    /** Map a function over a collection and return a Set. */
    public static <E1, E2,
                   C extends Collection<E1>,
                   F extends Function<E1, E2>> Set<E2> mapCollS(F f, C c) {
        return c.stream().map(f).collect(toSetC());
    }

    /** Filter a function over a collection and return a List. */
    public static <E,
                   C extends Collection<E>,
                   P extends Predicate<E>> List<E> filterCollL(P p, C c) {
        return c.stream().filter(p).collect(toListC());
    }

    /** Filter a function over a collection and return a Set. */
    public static <E,
                   C extends Collection<E>,
                   P extends Predicate<E>> Set<E> filterCollS(P p, C c) {
        return c.stream().filter(p).collect(toSetC());
    }

    /**
     * Add all the elements of an array {@code A[]} to a given
     * {@code Collection<T>} where {@code A extends T}.
     * <br>
     * Any underlying ordering to the given collection will be respected,
     * assuming that that collection implements {@link Collection#addAll addAll}
     * in a way that respects ordering.
     * <br>
     * Should run in <code>O(m + n)</code> time and <code>O(m + n)</code> space,
     * where <code>m = col.size()</code> and <code>n = arr.length</code>
     * Should be thread-safe (it synchronizes on {@code col} and {@code arr}).
     */
    public static <T, A extends T,
                   C extends Collection<T>> C addMany(C c, A... a) {
        List<T> al = newArrayList(c.size() + a.length + 1);
        synchronized(c) { al.addAll(c); }
        synchronized(a) { for(int i = 0; i < a.length; i++) { al.add(a[i]); } }
        return (C) al;
    }


    // ------------------------------------------------------------------------
    // ---------------------------- Set functions -----------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the Sets.newHashSet() pattern */
    public static <E> HashSet<E> newHashSet() {
        return Sets.newHashSet();
    }


    // ------------------------------------------------------------------------
    // ---------------------------- Map functions -----------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the Maps.newHashMap() pattern */
    public static <K, V> HashMap<K, V> newHashMap() {
        return Maps.newHashMap();
    }


    // ------------------------------------------------------------------------
    // ---------------------------- List functions ----------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the Lists.newArrayList pattern */
    public static <E> ArrayList<E> newArrayList() {
        return Lists.newArrayList();
    }

    /** Eliminates the Lists.newArrayListWithCapacity pattern */
    public static <E> ArrayList<E> newArrayList(int capacity) {
        return newArrayListWithCapacity(capacity);
    }

    /** Eliminates the Lists.newArrayListWithCapacity pattern */
    public static <E> ArrayList<E> newArrayListWithCapacity(int capacity) {
        return Lists.newArrayListWithCapacity(capacity);
    }

    /** Eliminates the Lists.newLinkedList pattern */
    public static <E> LinkedList<E> newLinkedList() {
        return Lists.newLinkedList();
    }

    /** Eliminates the Arrays.asList pattern */
    public static <E> List<E> asList(E... elements) {
        return Arrays.asList(elements);
    }

    /** Eliminates the Collections.singletonList pattern */
    public static <E> List<E> singletonList(E element) {
        return Collections.singletonList(element);
    }

    /** Convert a list of values to a list of pairs of values */
    public static <E> List<Pair<E, E>> toPairsL(List<E> elements) {
        if(elements.size() % 2 != 0) {
            throw kemCriticalErrorF("toPairs: odd number of elements");
        }

        Iterator<E> it = elements.iterator();
        List<Pair<E, E>> res = newArrayList(3 + elements.size() / 2);
        while(it.hasNext()) {
            E left  = it.next();
            E right = it.next();
            res.add(Pair.of(left, right));
        }
        return res;
    }

    /** Convert an array of values to a list of pairs of values */
    public static <E> List<Pair<E, E>> toPairsA(E... elements) {
        return toPairsL(asList(elements));
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
        List<Integer> result = newArrayList(elements + padding);
        for(int i = start; i <= stop; i += step) {
            result.add(Integer.valueOf(i));
        }
        return result;
    }

    /** Same as rangeInclusive except it excludes the first and last element */
    public static List<Integer> rangeExclusive(int start, int step, int stop) {
        List<Integer> result = rangeInclusive(start, step, stop);
        result.remove(0);
        result.remove(result.size() - 1);
        return result;
    }

    /** Same as rangeInclusive, except it defaults the step to 1 */
    public static List<Integer> rangeInclusive(int start, int stop) {
        return rangeInclusive(start, 1, stop);
    }

    /** Same as rangeInclusive, except it defaults the start to 0 */
    public static List<Integer> rangeInclusive(int stop) {
        return rangeInclusive(0, stop);
    }

    /** Same as rangeExclusive, except it defaults the step to 1 */
    public static List<Integer> rangeExclusive(int start, int stop) {
        return rangeExclusive(start, 1, stop);
    }

    /** Same as rangeExclusive, except it defaults the start to 0 */
    public static List<Integer> rangeExclusive(int stop) {
        return rangeExclusive(0, stop);
    }


    // ------------------------------------------------------------------------
    // --------------------------- Stream functions ---------------------------
    // ------------------------------------------------------------------------


    /** Eliminates the Collectors.toList pattern */
    public static <T> Collector<T, ?, List<T>> toListC() {
        return Collectors.toList();
    }

    /** Eliminates the Collectors.toSet pattern */
    public static <T> Collector<T, ?, Set<T>> toSetC() {
        return Collectors.toSet();
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joiningC() {
        return Collectors.joining();
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joiningC(String del) {
        return Collectors.joining(del);
    }

    /** Eliminates the Collectors.joining pattern */
    public static Collector<CharSequence, ?, String> joiningC(String del,
                                                              String pfx,
                                                              String sfx) {
        return Collectors.joining(del, pfx, sfx);
    }

    /** Eliminates the Collectors.groupingBy pattern */
    public static <T,K> Collector<T,?,Map<K,List<T>>>
    groupingByC(Function<? super T, ? extends K> classifier) {
        return Collectors.groupingBy(classifier);
    }


    // ------------------------------------------------------------------------
    // -------------------------- Process functions  --------------------------
    // ------------------------------------------------------------------------


    /**
     * Start a process with the given parameters
     * @param pb    A {@link ProcessBuilder} to use
     * @param dir   A directory in which to start the process
     * @param err   An output to which to send stderr
     * @param out   An output to which to send stdout
     * @param cmd   The command to run
     * @return      A {@link Process process} to wait on
     */
    public static Process startProcess(ProcessBuilder pb,
                                       File dir,
                                       ProcessBuilder.Redirect err,
                                       ProcessBuilder.Redirect out,
                                       String... cmd) throws IOException {
        ProcessBuilder pbPrime = pb;
        return pbPrime
            .command(cmd)
            .directory(dir)
            .redirectError(err)
            .redirectOutput(out)
            .start();
    }

    /**
     * Start a process with the given parameters
     * @param pb    A {@link ProcessBuilder} to use
     * @param dir   A directory in which to start the process
     * @param err   An output file to which to send stderr
     * @param out   An output file to which to send stdout
     * @param cmd   The command to run
     * @return      A {@link Process process} to wait on
     */
    public static Process startProcess(ProcessBuilder pb,
                                       File dir,
                                       File err,
                                       File out,
                                       String... cmd) throws IOException {
        return startProcess(pb, dir,
                            ProcessBuilder.Redirect.to(err),
                            ProcessBuilder.Redirect.to(out),
                            cmd);
    }

    /**
     * Start a process with the given parameters (doesn't redirect stdout/err)
     * @param pb    A {@link ProcessBuilder} to use
     * @param dir   A directory in which to start the process
     * @param cmd   The command to run
     * @return      A {@link Process process} to wait on
     */
    public static Process startProcess(ProcessBuilder pb,
                                       File dir,
                                       String... cmd) throws IOException {
        return startProcess(pb, dir,
                            ProcessBuilder.Redirect.INHERIT,
                            ProcessBuilder.Redirect.INHERIT,
                            cmd);
    }


    // ------------------------------------------------------------------------
    // ---------------------------- I/O functions -----------------------------
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
    // ------------------------- Exception functions --------------------------
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

    // ------------------ KEMException.criticalError aliases ------------------

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

    // ----------------- KEMException.innerParserError aliases ----------------

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

    // ------------------ KEMException.internalError aliases ------------------

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

    // ----------------- KEMException.outerParserError aliases ----------------

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
