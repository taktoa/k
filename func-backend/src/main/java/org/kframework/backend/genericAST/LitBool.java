// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class LitBool extends Exp {

    private final boolean val;

    public LitBool(boolean b, Target target) {
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
