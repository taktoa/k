package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.HashSet;

public class KSTModuleTerm extends KST {
    private final Set<KSTAtt> atts;

    protected KSTModuleTerm() {
        atts = new HashSet<>();
    }

    protected KSTModuleTerm(Set<KSTAtt> atts) {
        this.atts = atts;
    }

    protected void addAtt(KSTAtt a) {
        atts.add(a);
    }

    public Set<KSTAtt> getAtts() {
        return atts;
    }
}
