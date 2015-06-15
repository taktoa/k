// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class VarFPattern extends FPattern {

    private final FVariable var;

    public VarFPattern(FTarget target) {
        super(target);
        var = new FVariable(target);
    }

    public FVariable getFVariable() {
        return var;
    }

}
