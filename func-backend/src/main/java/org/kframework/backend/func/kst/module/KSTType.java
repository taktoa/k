package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;
import java.util.Map;

public class KSTType extends KSTModuleTerm {
    private final KSTSort sort;
    private final Map<KSTLabel, List<KSTSort>> cons;
    
    public KSTType(KSTSort sort,
                   Map<KSTLabel, List<KSTSort>> cons) {
        super();
        this.sort = sort;
        this.cons = cons;
    }

    public KSTType(KSTSort sort,
                   Map<KSTLabel, List<KSTSort>> cons,
                   Set<KSTAtt> atts) {
        super(atts);
        this.sort = sort;
        this.cons = cons;
    }

    public KSTSort getSort() {
        return sort;
    }

    public Map<KSTLabel, List<KSTSort>> getConstructors() {
        return cons;
    }

    @Override
    public String toString() {
        KSTLabel kl;
        StringBuilder sb = new StringBuilder();
        sb.append("(type ");
        sb.append(sort);
        sb.append(" ");
        for(Map.Entry<KSTLabel, List<KSTSort>> e : cons.entrySet()) {
            kl = e.getKey();
            sb.append("\n      ");
            sb.append("(");
            sb.append(kl);
            for(KSTSort s : e.getValue()) {
                sb.append(" ");
                sb.append(s);
            }
            sb.append(")");
        }
        sb.append(")\n");
        return sb.toString();
    }
}
