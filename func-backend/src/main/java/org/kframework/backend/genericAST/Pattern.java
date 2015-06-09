// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class Pattern extends GenericASTNode{

    protected final Target target;

    public Pattern(Target target) {
        super(target);
        this.target = target;
    }

}
