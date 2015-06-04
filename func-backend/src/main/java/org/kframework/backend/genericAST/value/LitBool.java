// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public abstract class LitBool extends Exp {

    private boolean val;

    public LitBool(boolean b) {
        val = b;
    }

    public boolean getValue() {
        return val;
    }

}
