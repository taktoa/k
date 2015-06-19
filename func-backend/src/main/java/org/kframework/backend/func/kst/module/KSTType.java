package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;
import java.util.Map;

public class KSTType extends KSTModuleTerm {
    private final KSTSort sort;
    private final Map<KSTLabel, List<KSTSort>> cons;
    private final Map<KSTLabel, KSTAttSet> conAtts;

    public KSTType(KSTSort sort,
                   Map<KSTLabel, List<KSTSort>> cons,
                   Map<KSTLabel, KSTAttSet> conAtts) {
        super();
        this.sort = sort;
        this.cons = cons;
        this.conAtts = conAtts;
    }

    public KSTType(KSTSort sort,
                   Map<KSTLabel, List<KSTSort>> cons,
                   Map<KSTLabel, KSTAttSet> conAtts,
                   KSTAttSet atts) {
        this(sort, cons, conAtts);
        super.setAtts(atts);
    }

    public KSTSort getSort() {
        return sort;
    }

    public Map<KSTLabel, List<KSTSort>> getConstructors() {
        return cons;
    }

    public Map<KSTLabel, KSTAttSet> getConstructorAtts() {
        return conAtts;
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
            sb.append(" ");
            sb.append(conAtts.get(kl));
            sb.append(")");
        }
        sb.append(")\n");
        return sb.toString();
    }
}
