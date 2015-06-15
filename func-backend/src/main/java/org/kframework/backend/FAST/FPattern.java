// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class FPattern extends FASTNode{

    protected final FTarget target;

    public FPattern(FTarget target) {
        super(target);
        this.target = target;
    }

}
