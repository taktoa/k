package org.kframework.backend.func.kst;

public class KSTVariable extends KSTTerm {
    private final String name;
    
    public KSTVariable(String n) {
        super();
        name = n;
    }

    public KSTVariable(String n, KSTSort s) {
        super();
        name = n;
        super.setSort(s);
    }

    public String toString() {
        return String.format("(variable %s : %s)", name, super.getSort());
    }
}
