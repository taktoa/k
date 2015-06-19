package org.kframework.backend.func.kst;

public class KSTToken extends KSTTerm {
    private final String token;

    public KSTToken(String t) {
        super();
        token = t;
    }

    public KSTToken(String t, KSTSort s) {
        super();
        token = t;
        super.setSort(s);
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTToken) {
            return o.hashCode() == hashCode();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        return String.format("(token %s : %s)", token, super.getSort());
    }
}
