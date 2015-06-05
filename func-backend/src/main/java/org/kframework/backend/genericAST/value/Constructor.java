// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.ConstructorName;
import org.kframework.backend.genericAST.type.ConstructorSignature;
import org.kframework.backend.genericAST.Target;

/**
 * @author: Sebastian Conybeare
 */
public class Constructor extends Exp {

    private final ConstructorName name;
    private final ConstructorSignature sig;

    public Constructor(ConstructorSignature cSig, Target target) {
        super(target);
        name = new ConstructorName(target);
        sig = cSig;
    }

    public ConstructorName getConstructorName() {
        return name;
    }

    public ConstructorSignature getConstructorSignature() {
        return sig;
    }

    public String unparse() {
        return target.unparse(this);
    }


}
