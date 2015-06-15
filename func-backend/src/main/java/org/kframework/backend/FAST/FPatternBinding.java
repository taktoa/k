// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FPatternBinding extends FGenericASTNode {

    private final FPattern lhs;
    private final FExp rhs;

    public FPatternBinding(FTarget target, FPattern lhs, FExp rhs) {
        super(target);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public FPattern getLHS() {
        return lhs;
    }

    public FExp getRHS() {
        return rhs;
    }

}
