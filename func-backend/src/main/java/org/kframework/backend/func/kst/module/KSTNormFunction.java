package org.kframework.backend.func.kst;

import java.util.List;
import java.util.stream.Collectors;

public class KSTNormFunction extends KSTModuleTerm {
    private final KSTLabel label;
    private final KSTSort resultSort;
    private final List<KSTVariable> args;
    private final KSTExpr body;

    public KSTNormFunction(KSTLabel label,
                           KSTSort resultSort,
                           List<KSTVariable> args,
                           KSTExpr body) {
        super();
        this.label      = label;
        this.resultSort = resultSort;
        this.args       = args;
        this.body       = body;
    }

    public KSTNormFunction(KSTLabel label,
                           KSTSort resultSort,
                           List<KSTVariable> args,
                           KSTExpr body,
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

    public List<KSTVariable> getArgs() {
        return args;
    }

    public KSTExpr getBody() {
        return body;
    }

    @Override
    public String toString() {
        List<KSTSort> sortL = args.stream().map(v -> v.getSort()).collect(Collectors.toList());
        sortL.add(resultSort);
        KSTSort fs = KSTSortArrow.createFromSortList(sortL);

        String argStr = args.stream()
                            .map(v -> v.getName())
                            .collect(Collectors.joining(" "));

        return String.format("(func %s (%s) %s : %s)\n", label, argStr, body, fs);
    }
}
