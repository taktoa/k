package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTTermEndomorphism implements UnaryOperator<KSTTerm> {
    @Override
    public final KSTTerm apply(KSTTerm t) {
        if(t instanceof KSTApply) {
            return apply((KSTApply) t);
        } else if(t instanceof KSTLabel) {
            return apply((KSTLabel) t);
        } else if(t instanceof KSTPrim) {
            return apply((KSTPrim) t);
        } else if(t instanceof KSTRewrite) {
            return apply((KSTRewrite) t);
        } else if(t instanceof KSTToken) {
            return apply((KSTToken) t);
        } else if(t instanceof KSTVariable) {
            return apply((KSTVariable) t);
        } else {
            throw KEMException.criticalError("Unknown subclass of KSTTerm: "
                                             + t.getClass().getName());
        }
    }

    public KSTTerm apply(KSTApply k) {
        KSTTerm resultLabelTerm = apply(k.getLabel());
        List<KSTTerm> resultArgs = k.getArgs()
                                    .stream()
                                    .map(this::apply)
                                    .collect(Collectors.toList());
        KSTLabel resultLabel;

        if(resultLabelTerm instanceof KSTLabel) {
            resultLabel = (KSTLabel) resultLabelTerm;
        } else {
            throw KEMException.criticalError("Cannot put "
                                             + resultLabelTerm.getClass().getName()
                                             + " as the label in a KSTApply");
        }

        return (KSTTerm) (new KSTApply(resultLabel, resultArgs, k.getSort()));
    }

    public KSTTerm apply(KSTLabel k) {
        return (KSTTerm) k;
    }

    public KSTTerm apply(KSTPrim k) {
        return (KSTTerm) k;
    }

    public KSTTerm apply(KSTRewrite k) {
        return (KSTTerm) (new KSTRewrite(apply(k.getLHS()), apply(k.getRHS())));
    }

    public KSTTerm apply(KSTToken k) {
        return (KSTTerm) k;
    }

    public KSTTerm apply(KSTVariable k) {
        return (KSTTerm) k;
    }
}
