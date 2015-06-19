package org.kframework.backend.func.kst;

import java.util.Optional;
import java.util.function.Function;

public final class KSTTermExpr extends KSTExpr {
    private final KSTTerm term;

    public KSTTermExpr(KSTTerm term) {
        this.term = term;
    }

    public KSTTerm getTerm() {
        return term;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTTermExpr) {
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
        return String.format("(expr %s)", term);
    }
}
