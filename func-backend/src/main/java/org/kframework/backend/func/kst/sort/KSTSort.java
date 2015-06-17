package org.kframework.backend.func.kst;

public class KSTSort {
    private final String name;
    
    public KSTSort(String n) {
        name = n;
    }
    
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTSort) {
            // FIXME(remy): a hack to simulate subsorting on Any
            String on = ((KSTSort) o).getName();
            if("Any".equals(on)) {
                return true;
            } else if("Any".equals(name)) {
                return true;
            } else {
                return name.equals(on);
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
