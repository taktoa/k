package org.kframework.backend.func.kst;

import java.util.List;

public class KSTApply extends KSTTerm {
    private final KSTLabel label;
    private final List<KSTTerm> args;
    private final KSTSort sort;
    
    public KSTApply(KSTLabel l, List<KSTTerm> a) {
        label = l;
        args = a;
        sort = new KSTSortAny();
    }

    public KSTApply(KSTLabel l, List<KSTTerm> a, KSTSort s) {
        label = l;
        args = a;
        sort = s;
    }

    public KSTLabel getLabel() {
        return label;
    }

    public List<KSTTerm> getArgs() {
        return args;
    }

    public KSTSort getSort() {
        return sort;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(KSTTerm t : args) {
            sb.append(" ");
            sb.append(t);
        }
        return String.format("(apply %s %s : %s)", label, sb.toString(), sort);
    }
}
