package org.kframework.backend.func.kst;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import org.kframework.utils.errorsystem.KEMException;

public class KSTSortArrow extends KSTSort {
    private final KSTSort from;
    private final KSTSort to;

    public KSTSortArrow(KSTSort f, KSTSort t) {
        super(String.format("%s -> %s", f, t));
        from = f;
        to = t;
    }

    public static KSTSort createFromSortList(Collection<KSTSort> sorts) {
        List<KSTSort> sortL = sorts.stream().collect(Collectors.toList());

        if(sortL.size() == 1) {
            return sortL.get(0);
        }

        if(sortL.size() == 2) {
            return new KSTSortArrow(sortL.get(0), sortL.get(1));
        }

        List<KSTSort> init = new ArrayList<>(sortL);
        int idx = init.size() - 1;
        KSTSort last = init.get(idx);
        init.remove(idx);
        return new KSTSortArrow(createFromSortList(init), last);
    }

    public KSTSort getFromSort() {
        return from;
    }

    public KSTSort getToSort() {
        return to;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTSortArrow) {
            KSTSortArrow arrow = (KSTSortArrow) o;
            return from.equals(arrow.getFromSort())
                &&   to.equals(arrow.getToSort());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return from.hashCode() + 2 * to.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", from, to);
    }
}
