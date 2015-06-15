// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FLitString extends FExp {

    private final String val;

    public FLitString(String val, FTarget target) {
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
