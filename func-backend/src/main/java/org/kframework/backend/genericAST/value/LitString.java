// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public class LitString extends Exp {

    private String val;

    public LitString(String s) {
        val = s;
    }

    public String getValue() {
        return val;
    }
    
}
