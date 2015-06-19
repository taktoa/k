package org.kframework.backend.func.kst;

public class KSTModuleTerm extends KST {
    private KSTAttSet atts;

    protected KSTModuleTerm() {
        this.atts = new KSTAttSet();
    }

    protected KSTModuleTerm(KSTAttSet atts) {
        setAtts(atts);
    }

    protected final void addAtt(KSTAtt a) {
        atts.addAtt(a);
    }

    protected final void setAtts(KSTAttSet atts) {
        this.atts = atts;
    }

    public final KSTAttSet getAtts() {
        return new KSTAttSet(atts);
    }
}
