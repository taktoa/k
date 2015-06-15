// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class FDeclarable extends FASTNode {

    protected final FTarget target;

    protected FDeclarable(FTarget target) {
        super(target);
        this.target = target;
    }

    public abstract String declare();

}
