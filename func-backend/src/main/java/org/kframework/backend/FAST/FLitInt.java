// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FLitInt extends FExp {
    
    private final int val;
    
    public FLitInt(int i, FTarget target) {
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
