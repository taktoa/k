// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class LitString extends Exp {

    private final String val;

    public LitString(String val, Unparser unparser) {
        super(unparser);
        this.val = val;
    }

    public String getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return unparser.unparse(this);
    }

    
}
