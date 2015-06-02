// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public class LitInt extends Exp {
    
    private int val;
    
    public LitInt(int i) {
        val = i;
    }

    public int getValue() {
        return val;
    }
    
}
