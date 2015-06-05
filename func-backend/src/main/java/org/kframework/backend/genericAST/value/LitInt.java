// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class LitInt extends Exp {
    
    private final int val;
    
    public LitInt(int i, Unparser unparser) {
        super(unparser);
        val = i;
    }

    public int getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return unparser.unparse(this);
    }

    
}
