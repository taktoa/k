package org.kframework.backend.func.kst;

public class KSTModuleDefinition extends KSTModuleTerm {
    private final KSTLabel label;

    protected KSTModuleDefinition(KSTLabel label) {
        super();
        this.label = label;
    }

    protected KSTModuleDefinition(KSTLabel label, KSTAttSet atts) {
        this(label);
        super.setAtts(atts);
    }

    public KSTLabel getLabel() {
        return label;
    }
}
