// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class If extends Exp {
    
    private final Exp condition;
    private final Exp trueBranch;
    private final Exp falseBranch;

    public If(Exp cond, Exp t, Exp f, Unparser unparser) {
        super(unparser);
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
        return unparser.unparse(this);
    }


}
