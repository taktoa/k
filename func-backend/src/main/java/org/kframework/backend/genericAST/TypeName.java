// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class TypeName extends Identifier {

    public TypeName(Target target) {
        super(target.newTypeName());
    }

}
