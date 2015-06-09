// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class Match extends Exp {

    private final ImmutableList<PatternBinding> cases;

    public Match(Target target, ImmutableList<PatternBinding> cases) {
        super(target);
        this.cases = cases;
    }

    public Match(Target target, PatternBinding... cases) {
        super(target);
        this.cases = ImmutableList.copyOf(cases);
    }

    public ImmutableList<PatternBinding> getCases() {
        return cases;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
