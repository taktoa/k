package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

public class KSTRuleEndomorphism <TT extends UnaryOperator<KSTTerm>,
                                  AT extends UnaryOperator<KSTAtt>>
                                 implements UnaryOperator<KSTRule> {
    private final TT termEndo;
    private final AT attEndo;
    
    public KSTRuleEndomorphism(TT t, AT a) {
        termEndo = t;
        attEndo = a;
    }

    @Override
    public KSTRule apply(KSTRule kr) {
        KSTTerm newBody     = termEndo.apply(kr.getBody());
        KSTTerm newRequires = termEndo.apply(kr.getRequires());
        KSTTerm newEnsures  = termEndo.apply(kr.getEnsures());
        Set<KSTAtt> newAtts = kr.getAtts()
                                .stream()
                                .map(attEndo::apply)
                                .collect(Collectors.toSet());
        
        return new KSTRule(newBody, newRequires, newEnsures, newAtts);
    }
}
