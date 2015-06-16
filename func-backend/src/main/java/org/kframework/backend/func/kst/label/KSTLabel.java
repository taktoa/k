package org.kframework.backend.func.kst;

public class KSTLabel extends KSTTerm {
    private final String name;
    
    public KSTLabel(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTLabel) {
            return name.equals(((KSTLabel) o).getName());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }
}
