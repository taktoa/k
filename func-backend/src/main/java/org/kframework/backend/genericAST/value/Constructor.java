// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.ConstructorName;
import org.kframework.backend.genericAST.type.ConstructorSignature;
import org.kframework.backend.genericAST.NamespaceManager;
import org.kframework.backend.genericAST.Unparser;

/**
 * @author: Sebastian Conybeare
 */
public class Constructor extends Exp {

    private final ConstructorName name;
    private final ConstructorSignature sig;

    public Constructor(ConstructorName cName, ConstructorSignature cSig, Unparser unparser) {
        super(unparser);
        name = cName;
        sig = cSig;
    }

    public Constructor(NamespaceManager nm, ConstructorSignature cSig, Unparser unparser) {
        super(unparser);
        name = nm.newConstructorName();
        sig = cSig;
    }

    public ConstructorName getConstructorName() {
        return name;
    }

    public ConstructorSignature getConstructorSignature() {
        return sig;
    }

    public String unparse() {
        return unparser.unparse(this);
    }


}
