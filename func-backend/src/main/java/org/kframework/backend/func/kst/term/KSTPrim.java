package org.kframework.backend.func.kst;

public class KSTPrim<Prim> extends KSTTerm {
    private final Prim prim;
    private final KSTSort sort;
    
    public KSTPrim(Prim p) {
        prim = p;
        sort = new KSTSortAny();
    }

    public KSTPrim(Prim p, KSTSort s) {
        prim = p;
        sort = s;
    }

    public String toString() {
        return String.format("((prim %s) : %s)", prim, sort);
    }
}
