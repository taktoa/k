package org.kframework.backend.func.kst;

import java.util.Set;

public class KSTRule extends KST {
    private final KSTTerm body;
    private final KSTTerm requires;
    private final KSTTerm ensures;
    private final Set<KSTAtt> atts;
    
    public KSTRule(KSTTerm b, KSTTerm r, KSTTerm e, Set<KSTAtt> a) {
        body = b;
        requires = r;
        ensures = e;
        atts = a;
    }

    public KSTTerm getBody() {
        return body;
    }

    public KSTTerm getRequires() {
        return requires;
    }

    public KSTTerm getEnsures() {
        return ensures;
    }

    public Set<KSTAtt> getAtts() {
        return atts;
    }

    public String toString() {
        return String.format("(rule %s %s %s %s)", body, requires, ensures, atts);
    }
}
