package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTModuleEndomorphism implements UnaryOperator<KSTModule> {
    private final KSTSyntaxEndomorphism syntaxEndo;
    private final KSTRuleEndomorphism   ruleEndo;

    public KSTModuleEndomorphism(KSTSyntaxEndomorphism kse,
                                  KSTRuleEndomorphism kre) {
        syntaxEndo = kse;
        ruleEndo = kre;
        next = nxt;
    }

    @Override
    public KSTModule apply(KSTModule km) {
        Set<KSTSyntax> newStx = km.getSyntax()
                                  .stream()
                                  .map(syntaxEndo::apply)
                                  .collect(Collectors.toSet());
        Set<KSTRule>   newRls = km.getRules()
                                  .stream()
                                  .map(ruleEndo::apply)
                                  .collect(Collectors.toSet());
        return new KSTModule(newStx, newRls);
    }
}
