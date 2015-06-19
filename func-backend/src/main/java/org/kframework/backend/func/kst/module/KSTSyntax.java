package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;

public class KSTSyntax extends KSTModuleDefinition {
    private final KSTSort sort;
    private final List<KSTSort> args;

    public KSTSyntax(KSTSort sort,
                     KSTLabel label,
                     List<KSTSort> args) {
        super(label);
        this.sort = sort;
        this.args = args;
    }

    public KSTSyntax(KSTSort sort,
                     KSTLabel label,
                     List<KSTSort> args,
                     KSTAttSet atts) {
        this(sort, label, args);
        super.setAtts(atts);
    }

    public KSTSort getSort() {
        return sort;
    }

    public List<KSTSort> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(KSTSort a : args) {
            sb.append(" ");
            sb.append(a);
        }
        return String.format("(syntax %s %s %s %s)",
                             sort,
                             getLabel(),
                             sb.toString(),
                             getAtts());
    }
}
