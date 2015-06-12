package org.kframework.backend.func.kst;

public class KSTPrim<Prim> extends KSTTerm {
    private final Prim prim;
    
    public KSTPrim(Prim p) {
        super();
        prim = p;
    }

    public KSTPrim(Prim p, KSTSort s) {
        super();
        prim = p;
        super.setSort(s);
    }

    public String toString() {
        return String.format("(prim %s : %s)", prim, super.getSort());
    }
}
