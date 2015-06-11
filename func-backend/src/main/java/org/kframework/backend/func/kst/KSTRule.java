package org.kframework.backend.func.kst;

import java.util.Set;

public class KSTRule extends KST {
    private final KSTTerm lhs;
    private final KSTTerm rhs;
    private final Set<KSTAtt> atts;
    
    public KSTRule(KSTTerm l, KSTTerm r, Set<KSTAtt> a) {
        lhs = l;
        rhs = r;
        atts = a;
    }

    public String toString() {
        return String.format("(rule %s %s %s)", lhs, rhs, atts);
    }
}
