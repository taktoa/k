// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class ConstructorName extends Identifier {

    public ConstructorName(Target target) {
        super(target.newConstructorName());
    }

}
