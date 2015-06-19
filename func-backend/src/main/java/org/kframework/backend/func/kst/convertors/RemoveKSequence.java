package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class RemoveKSequence extends KSTTermEndomorphism {
    private final static String seqName = new KSTLabelSeq().getName();

    private final static boolean isUnnecessaryKSTSeq(KSTApply k) {
        return seqName.equals(k.getLabel().getName())
            && k.getArgs().size() == 1;
    }

    public static final UnaryOperator<KSTModule> getModuleEndo() {
        KSTTermEndomorphism te = new RemoveKSequence();
        KSTRuleEndomorphism re = new KSTRuleEndomorphism(te, UnaryOperator.identity());
        KSTModuleTermEndomorphism mte = new KSTModuleTermEndomorphism(KSTRule.class, re);
        return (UnaryOperator<KSTModule>) new KSTModuleEndomorphism(mte);
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
