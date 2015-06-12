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

    public String toString() {
        return String.format("(token %s : %s)", token, super.getSort());
    }
}
