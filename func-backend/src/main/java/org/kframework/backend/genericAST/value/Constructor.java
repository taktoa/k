// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.ConstructorName;
import org.kframework.backend.genericAST.value.Exp;
import org.kframework.backend.genericAST.type.ConstructorSignature;

/**
 * @author: Sebastian Conybeare
 */
public class Constructor extends Exp {

    private ConstructorName name;
    private ConstructorSignature sig;

    public Constructor(ConstructorName cName, ConstructorSignature cSig) {
        name = cName;
        sig = cSig;
    }

    public ConstructorName getConstructorName() {
        return name;
    }

    public ConstructorSignature getConstructorSignature() {
        return sig;
    }

}
