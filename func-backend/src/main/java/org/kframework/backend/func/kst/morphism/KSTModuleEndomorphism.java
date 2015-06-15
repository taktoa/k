package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

public class KSTModuleEndomorphism implements UnaryOperator<KSTModule> {
    private final UnaryOperator<KSTSyntax> syntaxEndo;
    private final UnaryOperator<KSTRule>   ruleEndo;

    public KSTModuleEndomorphism(UnaryOperator<KSTSyntax> kse,
                                 UnaryOperator<KSTRule> kre) {
        syntaxEndo = kse;
        ruleEndo = kre;
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
