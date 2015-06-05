// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Target;
import org.kframework.backend.genericAST.ASTNode;
/**
 * @author: Sebastian Conybeare
 */
public abstract class Exp extends ASTNode {

    protected final Target target;

    public Exp(Target target) {
        super(target);
        this.target = target;
    }
    
    public abstract String unparse();
}
