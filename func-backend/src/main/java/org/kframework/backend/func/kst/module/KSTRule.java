package org.kframework.backend.func.kst;

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
                   KSTAttSet atts) {
        this(body, requires, ensures);
        super.setAtts(atts);
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
