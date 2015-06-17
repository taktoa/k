package org.kframework.backend.func.kst;

import java.util.Optional;
import java.util.function.Function;

public final class KSTPattern {
    private final KSTTerm term;

    private KSTPattern(KSTTerm term) {
        this.term = term;
    }

    public static Optional<KSTPattern> of(KSTTerm t,
                                          Function<KSTLabel, Boolean> isConstructor) {
        if(! checkIfValidPattern(t, isConstructor)) {
            return Optional.empty();
        }
        return Optional.of(new KSTPattern(t));
    }

    public KSTTerm getTerm() {
        return term;
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTPattern) {
            return o.hashCode() == hashCode();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return term.hashCode();
    }

    @Override
    public String toString() {
        return String.format("(pattern %s)", term);
    }

    private static boolean checkIfValidPattern(KSTTerm term,
                                               Function<KSTLabel, Boolean> allowed) {
        if(term instanceof KSTToken) {
            return true;
        } else if(term instanceof KSTPrim) {
            return true;
        } else if(term instanceof KSTRewrite) {
            return false;
        } else if(term instanceof KSTVariable) {
            return true;
        } else if(term instanceof KSTApply) {
            KSTApply app = (KSTApply) term;
            if(! allowed.apply(app.getLabel()).booleanValue()) {
                return false;
            }
            boolean result = true;
            for(KSTTerm t : app.getArgs()) {
                result &= checkIfValidPattern(t, allowed);
            }
            return result;
        }
        return false;
    }
}
