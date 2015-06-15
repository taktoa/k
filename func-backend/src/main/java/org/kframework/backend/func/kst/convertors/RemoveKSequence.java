package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class RemoveKSequence extends KSTTermEndomorphism {
    private final static String seqName = new KSTLabelSeq().getName();

    private final static boolean isUnnecessaryKSTSeq(KSTApply k) {
        return seqName.equals(k.getLabel().getName())
            && k.getArgs().size() == 1;
    }

    public static final KSTModuleEndomorphism getModuleEndo() {
        KSTTermEndomorphism te = new RemoveKSequence();
        KSTRuleEndomorphism re = new KSTRuleEndomorphism(te, UnaryOperator.identity());
        return new KSTModuleEndomorphism(new KSTModuleTermEndomorphism(KSTRule.class, re));
    }
    
    @Override
    public KSTTerm apply(KSTApply k) {
        if(isUnnecessaryKSTSeq(k)) {
            return this.apply(k.getArgs().get(0));
        } else {
            return super.apply(k);
        }
    }
}
