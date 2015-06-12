package org.kframework.backend.func.kst;

import java.util.List;

public class KSTSyntax extends KST {
    private final KSTSort sort;
    private final KSTLabel label;
    private final List<KSTSort> args;
    
    public KSTSyntax(KSTSort s, KSTLabel l, List<KSTSort> a) {
        sort = s;
        label = l;
        args = a;
    }

    public KSTSort getSort() {
        return sort;
    }

    public KSTLabel getLabel() {
        return label;
    }

    public List<KSTSort> getArgs() {
        return args;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(KSTSort a : args) {
            sb.append(" ");
            sb.append(a);
        }
        return String.format("(syntax %s %s %s)", sort, label, sb.toString());
    }
}
