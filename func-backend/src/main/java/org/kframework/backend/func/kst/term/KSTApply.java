package org.kframework.backend.func.kst;

import java.util.List;

public class KSTApply extends KSTTerm {
    private final KSTLabel label;
    private final List<KSTTerm> args;
    
    public KSTApply(KSTLabel l, List<KSTTerm> a) {
        super();
        label = l;
        args = a;
    }
    
    public KSTApply(KSTLabel l, List<KSTTerm> a, KSTSort s) {
        super();
        label = l;
        args = a;
        super.setSort(s);
    }

    public KSTLabel getLabel() {
        return label;
    }

    public List<KSTTerm> getArgs() {
        return args;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(KSTTerm t : args) {
            sb.append(" ");
            sb.append(t);
        }
        return String.format("(apply %s %s : %s)", label, sb, super.getSort());
    }
}
