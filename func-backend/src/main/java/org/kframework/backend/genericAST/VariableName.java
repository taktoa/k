// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class VariableName extends Identifier {

    public VariableName(Target target) {
        super(target.newVariable());
    }

}
