package org.kframework.backend.func.kst;

import java.util.Set;

public class KSTModuleDefinition extends KSTModuleTerm {
    private final KSTLabel label;

    protected KSTModuleDefinition(KSTLabel label) {
        super();
        this.label = label;
    }

    protected KSTModuleDefinition(KSTLabel label, Set<KSTAtt> atts) {
        super(atts);
        this.label = label;
    }

    public KSTLabel getLabel() {
        return label;
    }
}
