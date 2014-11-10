// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.ktest.Test;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.kframework.ktest.*;
import org.kframework.ktest.CmdArgs.KTestOptions;
import org.kframework.ktest.Config.InvalidConfigError;
import org.kframework.ktest.Config.LocationData;
import org.kframework.utils.OS;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class TestCase {

    /**
     * Absolute path of K definition file.
     */
    private final Annotated<String, LocationData> definition;

    /**
     * Absolute paths of program files directories.
     */
    private final List<Annotated<String, LocationData>> programs;

    /**
     * Valid extensions for programs. (without dot)
     */
    private final Set<String> extensions;

    /**
     * Program names that are excluded from krun processes.
     */
    private Set<String> excludes;

    /**
     * Absolute path of result files directories.
     */
    private final List<Annotated<String, LocationData>> results;

    /**
     * List of command line arguments to pass to kompile.
     */
    private List<PgmArg> kompileOpts;

    /**
     * List of command line arguments to pass to krun.
     */
    private ProgramProfile krunOpts;

    /**
     * Program full path, list of krun arguments pairs.
     */
    private Map<String, ProgramProfile> pgmSpecificKRunOpts;

    /**
     * Which tests to skip for this particular test case.
     */
    private Set<KTestStep> skips;

    /**
     * Script to be executed before testing, only valid on Posix system.
     * {@code null} if not available.
     */
    private String posixInitScript;

    /**
     * Full path of -d argument. {@code null} if -d is not provided.
     * NOTE: This should be generated again after calling {@link #setKompileOpts}.
     */
    private String kompileDirArg;

    /**
     * Full path of -kompiled directory that'll be generated by this test case.
     * NOTE: This should be generated again after calling {@link #setKompileOpts}.
     */
    private String kompileDirFullPath;

    /**
     * KTest flags and options.
     */
    private final KTestOptions options;

    private final KExceptionManager kem;
    private final FileUtil files;
    private final Map<String, String> env;

    public TestCase(Annotated<String, LocationData> definition,
                    List<Annotated<String, LocationData>> programs,
                    String[] extensions,
                    String[] excludes,
                    List<Annotated<String, LocationData>> results,
                    List<PgmArg> kompileOpts,
                    ProgramProfile krunOpts,
                    Map<String, ProgramProfile> pgmSpecificKRunOpts,
                    Set<KTestStep> skips, KTestOptions options,
                    KExceptionManager kem,
                    FileUtil files,
                    Map<String, String> env) {
        // programs and results should be ordered set because of how search algorithm works
        this.definition = definition;
        this.programs = programs;
        this.extensions = toSet(extensions);
        this.excludes = toSet(excludes);
        this.results = results;
        this.kompileOpts = kompileOpts;
        this.krunOpts = krunOpts;
        this.pgmSpecificKRunOpts = pgmSpecificKRunOpts;
        this.skips = skips;
        this.options = options;
        this.kem = kem;
        this.files = files;
        this.env = env;

        setKompileDirArg();
        setKompileDirFullPath();
    }

    public static TestCase makeTestCaseFromK(KTestOptions cmdArgs, KExceptionManager kem, FileUtil files, Map<String, String> env) {
        // give a warning if 'programs' is specified using the command line argument,
        // but 'extension' is not.
        if (cmdArgs.programsSpecified() && cmdArgs.getExtensions().isEmpty()) {
            kem.registerCompilerWarning("'programs' attribute is given, " +
                    "but 'extension' is not. ktest won't run any programs.");
        }

        Annotated<String, LocationData> targetFile =
                new Annotated<>(cmdArgs.getTargetFile(), new LocationData());

        List<Annotated<String, LocationData>> programs = new LinkedList<>();
        programs.add(new Annotated<>(cmdArgs.getResults(), new LocationData()));

        List<Annotated<String,LocationData>> results = new LinkedList<>();
        results.add(new Annotated<>(cmdArgs.getResults(), new LocationData()));

        List<PgmArg> emptyOpts = Collections.emptyList();
        ProgramProfile emptyProfile = new ProgramProfile(emptyOpts, false);

        Map<String, ProgramProfile> emptyOptsMap = Collections.emptyMap();

        return new TestCase(targetFile, programs,
                cmdArgs.getExtensions().toArray(new String[cmdArgs.getExtensions().size()]),
                cmdArgs.getExcludes().toArray(new String[cmdArgs.getExcludes().size()]),
                results, emptyOpts, emptyProfile, emptyOptsMap,
                new HashSet<>(), cmdArgs, kem, files, env);
    }

    public boolean isDefinitionKompiled() {
        return files.resolveWorkingDirectory(kompileDirFullPath).isDirectory();
    }

    /**
     * @return {@link org.kframework.ktest.Proc} that runs Posix-only command of the test case.
     *         {@code null} if test case doesn't have a Posix-only command.
     */
    public Proc<TestCase> getPosixOnlyProc() {
        if (posixInitScript == null) {
            return null;
        }
        return new Proc<>(this, getPosixOnlyCmd(), getWorkingDir(), options, kem, env);
    }

    /**
     * @return {@link org.kframework.ktest.Proc} that runs kompile command of the test case.
     */
    public Proc<TestCase> getKompileProc() {
        return new Proc<>(this, getKompileCmd(), getWorkingDir(), options, kem, env);
    }

    /**
     * @return {@link org.kframework.ktest.Proc} that runs PDF command of the test case.
     */
    public Proc<TestCase> getPDFProc() {
        return new Proc<>(this, getPdfCmd(), getWorkingDir(), options, kem, env);
    }

    /**
     * @return List of {@link Proc}s that run KRun commands of the test case.
     */
    public List<Proc<KRunProgram>> getKRunProcs() {
        List<Proc<KRunProgram>> procs = new ArrayList<>();

        for (KRunProgram program : getPrograms()) {
            String[] args = program.getKrunCmd();

            // passing null to Proc is OK, it means `ignore'
            String inputContents = null, outputContents = null, errorContents = null;
            if (program.inputFile != null)
                try {
                    inputContents = IOUtils.toString(new FileInputStream(program.inputFile));
                } catch (IOException e) {
                    System.out.format("WARNING: cannot read input file %s -- skipping program %s%n",
                            program.inputFile, program.args.get(1));
                    // this case happens when an input file is found by TestCase,
                    // but somehow file is not readable. in that case there's no point in running the
                    // program because it'll wait for input forever.
                    return null;
                }
            if (program.outputFile != null)
                try {
                    outputContents = IOUtils.toString(new FileInputStream(
                            program.outputFile));
                } catch (IOException e) {
                    System.out.format("WARNING: cannot read output file %s -- program output " +
                            "won't be matched against output file%n", program.outputFile);
                }
            if (program.errorFile != null)
                try {
                    errorContents = IOUtils.toString(new FileInputStream(
                            program.errorFile));
                } catch (IOException e) {
                    System.out.format("WARNING: cannot read error file %s -- program error output "
                            + "won't be matched against error file%n", program.errorFile);
                }

            // Annotate expected output and error messages with paths of files that these strings
            // are defined in (to be used in error messages)
            Annotated<String, File> outputContentsAnn = null;
            if (outputContents != null)
                outputContentsAnn = new Annotated<>(outputContents, program.outputFile);

            Annotated<String, File> errorContentsAnn = null;
            if (errorContents != null)
                errorContentsAnn = new Annotated<>(errorContents, program.errorFile);

            StringMatcher matcher = options.getDefaultStringMatcher();
            if (program.regex) {
                matcher = new RegexStringMatcher();
            }
            Proc<KRunProgram> p = new Proc<>(program, args, program.inputFile, inputContents,
                    outputContentsAnn, errorContentsAnn, matcher, program.defPath, options,
                    program.outputFile, program.newOutputFile, kem, env);
            procs.add(p);
        }

        return procs;
    }

    /**
     * @return absolute path of definition file
     */
    public String getDefinition() {
        assert files.resolveWorkingDirectory(definition.getObj()).isFile();
        return definition.getObj();
    }

    public File getWorkingDir() {
        File f = files.resolveWorkingDirectory(definition.getObj());
        assert f.isFile();
        return f.getParentFile();
    }

    public void setKompileOpts(List<PgmArg> kompileOpts) {
        this.kompileOpts = kompileOpts;
        setKompileDirArg();
        setKompileDirFullPath();
    }

    public void setExcludes(String[] excludes) {
        this.excludes = toSet(excludes);
    }

    public void setSkips(Set<KTestStep> skips) {
        this.skips = skips;
    }

    public void setKrunOpts(ProgramProfile krunOpts) {
        this.krunOpts = krunOpts;
    }

    public void setPgmSpecificKRunOpts(Map<String, ProgramProfile> pgmSpecificKRunOpts) {
        this.pgmSpecificKRunOpts = pgmSpecificKRunOpts;
    }

    public void setPosixInitScript(String posixInitScript) {
        this.posixInitScript = posixInitScript;
    }

    public void addProgram(Annotated<String, LocationData> program) {
        programs.add(program);
    }

    public void addResult(Annotated<String, LocationData> result) {
        results.add(result);
    }

    public String getPosixInitScript() {
        return posixInitScript;
    }

    /**
     * Do we need to skip a step for this test case?
     * @param step step to skip
     * @return whether to skip the step or not
     */
    public boolean skip(KTestStep step) {
        return skips.contains(step);
    }

    public void validate() throws InvalidConfigError {
        if (!files.resolveWorkingDirectory(definition.getObj()).isFile())
            throw new InvalidConfigError(
                    "definition file " + definition.getObj() + " is not a file.",
                    definition.getAnn());
        for (Annotated<String, LocationData> p : programs)
            if (!files.resolveWorkingDirectory(p.getObj()).isDirectory())
                throw new InvalidConfigError(
                        "program directory " + p.getObj() + " is not a directory.",
                        p.getAnn());
        for (Annotated<String, LocationData> r : results)
            if (!files.resolveWorkingDirectory(r.getObj()).isDirectory())
                throw new InvalidConfigError(
                        "result directory " + r.getObj() + " is not a directory.",
                        r.getAnn());
    }

    /**
     * @return Full path of -kompiled directory that'll be generated by this test case.
     */
    public String getKompileDirFullPath() {
        return kompileDirFullPath;
    }

    /**
     * @return command array to pass process builder
     */
    private String[] getPosixOnlyCmd() {
        assert posixInitScript == null || files.resolveWorkingDirectory(posixInitScript).isFile();
        return new String[] { posixInitScript };
    }

    /**
     * @return command array to pass process builder
     */
    private String[] getKompileCmd() {
        assert files.resolveWorkingDirectory(getDefinition()).isFile();
        List<String> stringArgs = new ArrayList<>();
        stringArgs.add(ExecNames.getKompile());
        stringArgs.add(getDefinition());
        for (PgmArg kompileOpt : kompileOpts) {
            stringArgs.addAll(kompileOpt.toStringList());
        }
        String[] argsArr = stringArgs.toArray(new String[stringArgs.size()]);
        if (OS.current() == OS.WIN) {
            for (int i = 0; i < argsArr.length; i++) {
                argsArr[i] = StringUtil.escapeShell(argsArr[i], OS.current());
            }
        }
        return argsArr;
    }

    /**
     * @return command array to pass process builder
     */
    private String[] getPdfCmd() {
        assert files.resolveWorkingDirectory(getDefinition()).isFile();
        String definitionFilePath =
                FilenameUtils.getFullPathNoEndSeparator(definition.getObj());
        String kompileDir = kompileDirArg;
        if (kompileDir == null) {
            // Use default value: path of .k file
            kompileDir = definitionFilePath;
        }
        String[] argsArr =
                new String[] { ExecNames.getKDoc(), "--format", "pdf", "--directory", kompileDir };
        if (OS.current() == OS.WIN) {
            for (int i = 0; i < argsArr.length; i++) {
                argsArr[i] = StringUtil.escapeShell(argsArr[i], OS.current());
            }
        }
        return argsArr;
    }

    /**
     * Generate set of programs to run for this test case.
     * @return set of programs to krun
     */
    private List<KRunProgram> getPrograms() {
        List<KRunProgram> ret = new LinkedList<>();
        for (Annotated<String, LocationData> pgmDir : programs)
            ret.addAll(searchPrograms(pgmDir.getObj()));
        // at this point ret may contain programs with same names, what we want is to search
        // program directories right to left, and have at most one program with same name
        Set<String> pgmNames = new HashSet<>();
        for (int i = ret.size() - 1; i >= 0; i--) {
            String pgmName = FilenameUtils.getName(ret.get(i).pgmName);
            if (pgmNames.contains(pgmName))
                ret.remove(i);
            else
                pgmNames.add(pgmName);
        }
        return ret;
    }

    /**
     * Search for -d or --directory argument in kompile arguments of test case, and set
     * {@link #kompileDirArg} field.
     * NOTE: This method is correct as far as kompile keeps -d argument behavior same.
     * (e.g. it should resolve the directory by considering -d relative to full path of definition
     * file)
     */
    private void setKompileDirArg() {
        for (PgmArg karg : kompileOpts) {
            if (karg.arg.equals("--directory") || karg.arg.equals("-d")) {
                kompileDirArg = FilenameUtils.concat(
                        FilenameUtils.getFullPath(definition.getObj()), karg.val);
                return;
            }
        }
        kompileDirArg = null;
    }

    /**
     * Sets full path of -kompiled directory that'll be generated by this test case.
     */
    private void setKompileDirFullPath() {
        String dirArg = kompileDirArg;
        String dashKompiled = FilenameUtils.getBaseName(definition.getObj()) + "-kompiled";
        if (dirArg != null) {
            kompileDirFullPath = FilenameUtils.concat(dirArg, dashKompiled);
        } else {
            kompileDirFullPath = FilenameUtils.concat(
                    FilenameUtils.getFullPath(definition.getObj()), dashKompiled);
        }
    }

    private ProgramProfile getPgmOptions(String pgm) {
        ProgramProfile ret = pgmSpecificKRunOpts.get(FilenameUtils.getName(pgm));
        if (ret == null)
            return krunOpts;
        return ret;
    }

    /**
     * Search for program files by taking program extensions and excluded files into account.
     * @param pgmDir Root folder to start searching
     * @return list of KRunPrograms
     */
    private List<KRunProgram> searchPrograms(String pgmDir) {
        List<KRunProgram> ret = new LinkedList<>();
        File[] files = this.files.resolveWorkingDirectory(pgmDir).listFiles();
        assert files != null : "searchPrograms returned null -- this is a bug, please report.";
        for (File pgmFile : files) {
            if (pgmFile.isFile()) {
                String pgmFilePath = pgmFile.getAbsolutePath();
                if (extensions.contains(FilenameUtils.getExtension(pgmFilePath))) {
                    // skip excluded files
                    boolean exclude = false;
                    for (String excludedPattern : excludes)
                            if (pgmFilePath.contains(excludedPattern))
                                exclude = true;
                    if (exclude)
                        continue;

                    // find result files
                    String inputFileName = FilenameUtils.getName(pgmFilePath) + ".in";
                    String outputFileName = FilenameUtils.getName(pgmFilePath) + ".out";
                    String errorFileName = FilenameUtils.getName(pgmFilePath) + ".err";

                    String definitionFilePath =
                            FilenameUtils.getFullPathNoEndSeparator(definition.getObj());
                    String inputFilePath = searchFile(inputFileName, results);
                    String outputFilePath = searchFile(outputFileName, results);
                    String errorFilePath = searchFile(errorFileName, results);

                    // set krun args
                    List<PgmArg> args = new LinkedList<>();
                    ProgramProfile profile = getPgmOptions(pgmFilePath);
                    boolean hasDirectory = false;
                    for (PgmArg arg : profile.getArgs()) {
                        if (arg.arg.equals("--directory") || arg.arg.equals("-d")) {
                            hasDirectory = true;
                        }
                        args.add(arg);
                    }
                    if (!hasDirectory) {
                        // Use -d of kompile if it's provided
                        String kompileDir = kompileDirArg;
                        if (kompileDir == null) {
                            // Use default value: path of .k file
                            kompileDir = definitionFilePath;
                        }
                        args.add(new PgmArg("--directory", kompileDir));
                    }

                    ret.add(new KRunProgram(this, pgmFilePath,
                            this.files.resolveWorkingDirectory(definitionFilePath), args,
                            inputFilePath == null ? null : this.files.resolveWorkingDirectory(inputFilePath),
                            outputFilePath == null ? null : this.files.resolveWorkingDirectory(outputFilePath),
                            errorFilePath == null ? null : this.files.resolveWorkingDirectory(errorFilePath),
                            getNewOutputFilePath(outputFileName), profile.isRegex()));
                }
            } else {
                ret.addAll(searchPrograms(pgmFile.getAbsolutePath()));
            }
        }
        return ret;
    }

    private File getNewOutputFilePath(String outputFileName) {
        return files.resolveWorkingDirectory(FilenameUtils.concat(results.get(results.size() -1).getObj(), outputFileName));
    }

    /**
     * Search file in list of directories in reverse order.
     * Search is recursive, meaning that subfolders are also searched.
     * @param fname file name (not path)
     * @param dirs list of directories to search
     * @return absolute path if file is found, null otherwise
     */
    private String searchFile(String fname, List<Annotated<String, LocationData>> dirs) {
        ListIterator<Annotated<String, LocationData>> li = dirs.listIterator(dirs.size());
        while (li.hasPrevious()) {
            Annotated<String, LocationData> dir = li.previous();
            String ret = searchFile(fname, dir.getObj());
            if (ret != null)
                return ret;
        }
        return null;
    }

    /**
     * Search file recursively in dir.
     * @param fname file name
     * @param dir root directory to start searching
     * @return absolute path if found, null if not
     */
    private String searchFile(String fname, String dir) {
        File[] files = this.files.resolveWorkingDirectory(dir).listFiles();
        assert files != null : "listFiles returned null -- this is a bug, please report";
        for (File f : files) {
            if (f.isFile() && f.getName().equals(fname))
                return f.getAbsolutePath();
            else if (f.isDirectory()) {
                String ret = searchFile(fname, f.getAbsolutePath());
                if (ret != null)
                    return ret;
            }
        }
        return null;
    }

    private <T> Set<T> toSet(T[] arr) {
        Set<T> ret = new HashSet<>();
        Collections.addAll(ret, arr);
        return ret;
    }
}
