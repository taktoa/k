// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.ConstructorName;
import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public class Constructor extends Exp {

    private ConstructorName name;

    public Constructor(ConstructorName constructorName) {
        name = constructorName;
    }

    public ConstructorName getConstructorName() {
        return name;
    }

}
