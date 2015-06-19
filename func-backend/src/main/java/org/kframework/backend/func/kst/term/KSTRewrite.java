package org.kframework.backend.func.kst;

public class KSTRewrite extends KSTTerm {
    private final KSTTerm lhs;
    private final KSTTerm rhs;

    public KSTRewrite(KSTTerm l, KSTTerm r) {
        lhs = l;
        rhs = r;
    }

    public KSTTerm getLHS() {
        return lhs;
    }

    public KSTTerm getRHS() {
        return rhs;
    }

    @Override
    public String toString() {
        return String.format("(rewrite %s %s)", lhs, rhs);
    }
}
