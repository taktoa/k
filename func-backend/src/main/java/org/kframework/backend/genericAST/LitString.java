// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class LitString extends Exp {

    private final String val;

    public LitString(String val, Target target) {
        super(target);
        this.val = val;
    }

    public String getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

    
}
