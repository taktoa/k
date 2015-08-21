// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.beust.jcommander.Parameter;
import org.kframework.utils.inject.RequestScoped;
import org.kframework.utils.options.StringListConverter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.kframework.backend.func.FuncUtil.*;

/**
 * Command-line options for the K Framework functional backend.
 * If you change anything in this class, you should adjust
 * FuncOptionsTest.java to compensate.
 *
 * @author Remy Goldschmidt
 */
@RequestScoped
public class FuncOptions implements Serializable {


    // ------------------------------------------------------------------------
    // ------------------------------ Parameters ------------------------------
    // ------------------------------------------------------------------------


    @Parameter(names         = { "-O", "--optimization" },
               description   = "<level> is an integer ranging from 0 to 3 "
                             + "representing the optimization level with "
                             + "which to kompile.")
    private String optLevel = "1";

    @Parameter(names         = { "--gen-ml-only" },
               description   = "Do not compile definition; only generate "
                             + ".ml files.")
    private boolean genMLOnly = false;

    @Parameter(names         = { "--profile-rules" },
               description   = "Enable profiling in generated ML.")
    private boolean profileRules = false;

    @Parameter(names         = { "--packages" },
               listConverter = StringListConverter.class,
               description   = "<string> is a whitespace-separated list of "
                             + "ocamlfind packages to be included in the "
                             + "compilation of the definition")
    private List<String> packages = Collections.emptyList();

    @Parameter(names         = { "--hook-namespaces" },
               listConverter = StringListConverter.class,
               description   = "<string> is a whitespace-separated list of "
                             + "namespaces to include in the hooks defined "
                             + "in the definition")
    private List<String> hookNamespaces = Collections.emptyList();

    @Parameter(names         = { "--no-link-prelude" },
               description   = "Do not link interpreter binaries against "
                             + "constants.cmx and prelude.cmx. Do not use "
                             + "this if you don't know what you're doing.")
    private boolean noLinkPrelude = false;


    // ------------------------------------------------------------------------
    // --------------------------- Enums / Classes ----------------------------
    // ------------------------------------------------------------------------


    /** The optimization level, represented as an enum. */
    public enum OptLevel {
        FAST,
        SLOW;
    }


    // ------------------------------------------------------------------------
    // ------------------------------- Methods --------------------------------
    // ------------------------------------------------------------------------


    /** Optimization level at which to kompile. */
    public Optional<OptLevel> getOptLevel() {
        switch(optLevel) {
        case "0": return Optional.of(OptLevel.SLOW);
        case "1": return Optional.of(OptLevel.FAST);
        case "2": return Optional.of(OptLevel.FAST);
        case "3": return Optional.of(OptLevel.FAST);
        default:  return Optional.empty();
        }
    }

    /** Should we only generate the ML files? */
    public boolean getGenMLOnly() {
        return genMLOnly;
    }

    /** Should we profile rules in the generated ML? */
    public boolean getProfileRules() {
        return profileRules;
    }

    /** Packages to add to the ocamlfind invocation. */
    public List<String> getPackages() {
        return packages;
    }

    /** Hook namespaces to include in the hooks defined in the definition. */
    public List<String> getHookNamespaces() {
        return hookNamespaces;
    }

    /** Should we link the prelude? */
    public boolean getNoLinkPrelude() {
        return noLinkPrelude;
    }
}
