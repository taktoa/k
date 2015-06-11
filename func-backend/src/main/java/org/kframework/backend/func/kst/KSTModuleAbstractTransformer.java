package org.kframework.backend.func.kst;

import java.util.List;

public abstract class KSTModuleAbstractTransformer<Out> {
    public abstract Out apply(List<KSTRule> r, List<KSTSyntax> s);
}
