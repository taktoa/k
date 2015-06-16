package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KSTFunction extends KSTModuleTerm {
    private final KSTLabel label;
    private final KSTSort resultSort;
    private final List<KSTTerm> args;
    private final KSTTerm body;

    public KSTFunction(KSTLabel label,
                       KSTSort resultSort,
                       List<KSTTerm> args,
                       KSTTerm body) {
        super();
        this.label      = label;
        this.resultSort = resultSort;
        this.args       = args;
        this.body       = body;
    }

    public KSTFunction(KSTLabel label,
                       KSTSort resultSort,
                       List<KSTTerm> args,
                       KSTTerm body,
                       Set<KSTAtt> atts) {
        super(atts);
        this.label      = label;
        this.resultSort = resultSort;
        this.args       = args;
        this.body       = body;
    }

    public KSTLabel getLabel() {
        return label;
    }

    public KSTSort getResultSort() {
        return resultSort;
    }

    public List<KSTTerm> getArgs() {
        return args;
    }

    public KSTTerm getBody() {
        return body;
    }

    @Override
    public String toString() {
        List<KSTSort> sortL = args.stream().map(v -> v.getSort()).collect(Collectors.toList());
        sortL.add(resultSort);
        KSTSort fs = KSTSortArrow.createFromSortList(sortL);

        String argStr = args.stream()
                            .map(v -> v.toString())
                            .collect(Collectors.joining(" ", "(", ")"));

        return String.format("(function %s %s : %s)\n", argStr, body, fs);
    }
}
