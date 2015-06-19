package org.kframework.backend.func.kst;

import java.util.List;
import java.util.stream.Collectors;

public class KSTAtt {
    private final KSTLabel label;
    private final List<KSTTerm> args;

    public KSTAtt(KSTLabel label, List<KSTTerm> args) {
        this.label = label;
        this.args = args;
    }

    public KSTAtt(KSTApply app) {
        label = app.getLabel();
        args = app.getArgs();
    }

    public KSTLabel getLabel() {
        return label;
    }

    public List<KSTTerm> getArgs() {
        return args;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTAtt) {
            KSTAtt a = (KSTAtt) o;
            return label.equals(a.getLabel())
                &&  args.equals(a.getArgs());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return label.hashCode() + (2 * args.hashCode());
    }

    @Override
    public String toString() {
        String argStr = args.stream()
                            .map(x -> x.toString())
                            .collect(Collectors.joining(", "));
        return String.format("%s(%s)", label, argStr);
    }
}
