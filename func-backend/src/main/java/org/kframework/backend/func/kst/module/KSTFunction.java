package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KSTFunction extends KSTModuleTerm {
    private final KSTLabel label;
    private final KSTSort resultSort;
    private final List<KSTPattern> args;
    private final KSTTerm body;

    public KSTFunction(KSTLabel label,
                       KSTSort resultSort,
                       List<KSTPattern> args,
                       KSTTerm body) {
        super();
        this.label      = label;
        this.resultSort = resultSort;
        this.args       = args;
        this.body       = body;
    }

    public KSTFunction(KSTLabel label,
                       KSTSort resultSort,
                       List<KSTPattern> args,
                       KSTTerm body,
                       KSTAttSet atts) {
        this(label, resultSort, args, body);
        super.setAtts(atts);
    }

    public KSTLabel getLabel() {
        return label;
    }

    public KSTSort getResultSort() {
        return resultSort;
    }

    public List<KSTPattern> getArgs() {
        return args;
    }

    public KSTTerm getBody() {
        return body;
    }

    public KSTSort getSort() {
        List<KSTSort> sortL = args.stream().map(v -> v.getTerm().getSort()).collect(Collectors.toList());
        sortL.add(resultSort);
        return KSTSortArrow.createFromSortList(sortL);
    }

    @Override
    public String toString() {
        String argStr = args.stream()
                            .map(v -> v.toString())
                            .collect(Collectors.joining(" "));

        return String.format("(function %s (%s) %s : %s)", label, argStr, body, getSort());
    }
}
