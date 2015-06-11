package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTRuleEndomorphism <RT extends UnaryOperator<KSTRule>>
                                 implements UnaryOperator<KSTRule> {
    private final RT ruleFunc;
    
    public KSTRuleEndomorphism(RT rf) {
        ruleFunc = rf;
    }

    @Override
    public KSTRule apply(KSTRule kr) {
        return ruleFunc.apply(kr);
    }
}
