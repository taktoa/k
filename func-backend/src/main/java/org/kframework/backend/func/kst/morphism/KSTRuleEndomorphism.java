package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

public class KSTRuleEndomorphism implements UnaryOperator<KSTRule> {
    private final UnaryOperator<KSTTerm> termEndo;
    private final UnaryOperator<KSTAtt>  attEndo;

    public KSTRuleEndomorphism(UnaryOperator<KSTTerm> t,
                               UnaryOperator<KSTAtt> a) {
        termEndo = t;
        attEndo = a;
    }

    @Override
    public KSTRule apply(KSTRule kr) {
        KSTTerm newBody     = termEndo.apply(kr.getBody());
        KSTTerm newRequires = termEndo.apply(kr.getRequires());
        KSTTerm newEnsures  = termEndo.apply(kr.getEnsures());
        Set<KSTAtt> newAtts = kr.getAtts()
                                .getAttSet()
                                .stream()
                                .map(attEndo::apply)
                                .collect(Collectors.toSet());

        return new KSTRule(newBody, newRequires, newEnsures, new KSTAttSet(newAtts));
    }
}
