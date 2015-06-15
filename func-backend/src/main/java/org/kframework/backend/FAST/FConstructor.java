// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FConstructor extends FExp {

    private final FConstructorName name;
    private final FConstructorSignature sig;

    public FConstructor(FConstructorSignature cSig, FTarget target) {
        super(target);
        name = new FConstructorName(target);
        sig = cSig;
    }

    public FConstructorName getFConstructorName() {
        return name;
    }

    public FConstructorSignature getFConstructorSignature() {
        return sig;
    }

    public String unparse() {
        return target.unparse(this);
    }


}
