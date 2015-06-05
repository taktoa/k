// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class LitInt extends Exp {
    
    private final int val;
    
    public LitInt(int i, Target target) {
        super(target);
        val = i;
    }

    public int getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

    
}
