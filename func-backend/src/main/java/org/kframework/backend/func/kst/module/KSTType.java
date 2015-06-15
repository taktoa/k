package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Map;

public class KSTType extends KSTModuleTerm {
    private final KSTSort sort;
    private final Map<KSTLabel, List<KSTSort>> cons;
    
    public KSTType(KSTSort s, Map<KSTLabel, List<KSTSort>> c) {
        sort = s;
        cons = c;
    }

    public KSTSort getSort() {
        return sort;
    }

    public Map<KSTLabel, List<KSTSort>> getConstructors() {
        return cons;
    }
    
    public String toString() {
        KSTLabel kl;
        StringBuilder sb = new StringBuilder();
        sb.append("(type ");
        sb.append(sort);
        sb.append(" ");
        for(Map.Entry<KSTLabel, List<KSTSort>> e : cons.entrySet()) {
            kl = e.getKey();
            sb.append("(");
            sb.append(kl);
            for(KSTSort s : e.getValue()) {
                sb.append(" ");
                sb.append(s);
            }
            sb.append(")");
            sb.append("\n      ");
        }
        sb.append(")\n");
        return sb.toString();
    }
}
