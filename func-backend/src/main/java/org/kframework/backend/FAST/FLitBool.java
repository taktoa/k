// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FLitBool extends FExp {

    private final boolean val;

    public FLitBool(boolean b, FTarget target) {
        super(target);
        val = b;
    }

    public boolean getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }


}
