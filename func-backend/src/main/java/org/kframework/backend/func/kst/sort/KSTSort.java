package org.kframework.backend.func.kst;

public class KSTSort {
    private final String name;
    
    public KSTSort(String n) {
        name = n;
    }
    
    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    public boolean equals(KSTSort s) {
        return name.equals(s.getName());
    }

    public int hashCode() {
        return name.hashCode();
    }
}
