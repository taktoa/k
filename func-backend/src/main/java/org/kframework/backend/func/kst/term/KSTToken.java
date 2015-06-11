package org.kframework.backend.func.kst;

public class KSTToken extends KSTTerm {
    private final String token;
    private final KSTSort sort;
    
    public KSTToken(String t) {
        token = t;
        sort = new KSTSortAny();
    }

    public KSTToken(String t, KSTSort s) {
        token = t;
        sort = s;
    }

    public KSTSort getSort() {
        return sort;
    }

    public String toString() {
        return String.format("((token %s) : %s)", token, sort);
    }
}
