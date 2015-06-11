package org.kframework.backend.func.kst;

public class KSTLabel extends KSTTerm {
    private final String name;
    
    public KSTLabel(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return getName();
    }
}
