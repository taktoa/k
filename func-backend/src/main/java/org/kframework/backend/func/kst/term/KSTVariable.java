package org.kframework.backend.func.kst;

public class KSTVariable extends KSTTerm {
    private final String name;
    private final KSTSort sort;
    
    public KSTVariable(String n) {
        name = n;
        sort = new KSTSortAny();
    }

    public KSTVariable(String n, KSTSort s) {
        name = n;
        sort = s;
    }

    public KSTSort getSort() {
        return sort;
    }

    public String toString() {
        return String.format("((variable %s) : %s)", name, sort);
    }
}
