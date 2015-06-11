package org.kframework.backend.func.kst.sort;

import org.kframework.backend.func.kst.KSTSort;

public class KSTSortArrow extends KSTSort {
    private final KSTSort from;
    private final KSTSort to;

    public KSTSortArrow(KSTSort f, KSTSort t) {
        super(String.format("%s -> %s", f, t));
        from = f;
        to = t;
    }
    
    public KSTSort getFromSort() {
        return from;
    }

    public KSTSort getToSort() {
        return to;
    }

    public String toString() {
        return String.format("%s -> %s", from, to);
    }
}
