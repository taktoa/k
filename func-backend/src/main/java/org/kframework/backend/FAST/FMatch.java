// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class FMatch extends FExp {

    private final ImmutableList<FPatternBinding> cases;

    public FMatch(FTarget target, ImmutableList<FPatternBinding> cases) {
        super(target);
        this.cases = cases;
    }

    public FMatch(FTarget target, FPatternBinding... cases) {
        super(target);
        this.cases = ImmutableList.copyOf(cases);
    }

    public ImmutableList<FPatternBinding> getCases() {
        return cases;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
