package org.kframework.backend.func.kst;

import java.util.Set;

public class KSTRule extends KSTModuleTerm {
    private final KSTTerm body;
    private final KSTTerm requires;
    private final KSTTerm ensures;

    public KSTRule(KSTTerm body,
                   KSTTerm requires,
                   KSTTerm ensures) {
        super();
        this.body = body;
        this.requires = requires;
        this.ensures = ensures;
    }

    public KSTRule(KSTTerm body,
                   KSTTerm requires,
                   KSTTerm ensures,
                   Set<KSTAtt> atts) {
        super(atts);
        this.body = body;
        this.requires = requires;
        this.ensures = ensures;
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

    @Override
    public String toString() {
        return String.format("(rule %s %s %s %s)\n", body, requires, ensures, getAtts());
    }
}
