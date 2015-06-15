package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTModuleTermEndomorphism <MT extends KSTModuleTerm> implements UnaryOperator<MT> {
    private final UnaryOperator<MT> mtEndo;
    private final Class<MT> mtClass;

    public KSTModuleTermEndomorphism(Class<MT> mtc, UnaryOperator<MT> mte) {
        mtClass = mtc;
        mtEndo = mte;
    }

    @Override
    public KSTModuleTerm apply(KSTModuleTerm kmt) {
        if(mtClass.isInstance(kmt)) {
            return (KSTModuleTerm) mtEndo.apply(mtClass.cast(kmt));
        } else {
            return kmt;
        }
    }
}
