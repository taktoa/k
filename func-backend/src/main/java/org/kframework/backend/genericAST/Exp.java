// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class Exp extends genericASTNode {

    protected final Target target;

    public Exp(Target target) {
        super(target);
        this.target = target;
    }
    
    public abstract String unparse();
}
