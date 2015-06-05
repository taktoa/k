// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class If extends Exp {
    
    private final Exp condition;
    private final Exp trueBranch;
    private final Exp falseBranch;

    public If(Exp cond, Exp t, Exp f, Target target) {
        super(target);
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

    @Override
    public String unparse() {
        return target.unparse(this);
    }


}
