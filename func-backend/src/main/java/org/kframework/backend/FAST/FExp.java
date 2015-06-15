// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class FExp extends FASTNode {

    protected final FTarget target;

    protected FExp(FTarget target) {
        super(target);
        this.target = target;
    }
    
    public abstract String unparse();
}
