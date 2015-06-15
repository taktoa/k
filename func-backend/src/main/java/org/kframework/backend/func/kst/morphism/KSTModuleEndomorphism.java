package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

public class KSTModuleEndomorphism implements UnaryOperator<KSTModule> {
    private final UnaryOperator<KSTModuleTerm> mtEndo;

    public KSTModuleEndomorphism(UnaryOperator<KSTModuleTerm> mte) {
        mtEndo = mte;
    }

    @Override
    public KSTModule apply(KSTModule km) {
        Set<KSTModuleTerm> newMts = km.getTerms()
                                      .stream()
                                      .map(mtEndo::apply)
                                      .collect(Collectors.toSet());
        return new KSTModule(newMts);
    }
}
