// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public class If extends Exp {
    
    private Exp condition;
    private Exp trueBranch;
    private Exp falseBranch;

    public If(Exp cond, Exp t, Exp f) {
        condition = cond;
        trueBranch = t;
        falseBranch = f;
    }

    public Exp getCondition() {
        return condition;
    }

    public Exp getTrueBranch() {
        return trueBranch;
    }

    public Exp getFalseBranch() {
        return falseBranch;
    }

}
