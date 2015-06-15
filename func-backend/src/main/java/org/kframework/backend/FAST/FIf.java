// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FIf extends FExp {
    
    private final FExp condition;
    private final FExp trueBranch;
    private final FExp falseBranch;

    public FIf(FExp cond, FExp t, FExp f, FTarget target) {
        super(target);
        condition = cond;
        trueBranch = t;
        falseBranch = f;
    }

    public FExp getCondition() {
        return condition;
    }

    public FExp getTrueBranch() {
        return trueBranch;
    }

    public FExp getFalseBranch() {
        return falseBranch;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }


}
