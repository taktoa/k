// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import com.google.common.collect.ImmutableList;

/**
 * @author: Sebastian Conybeare
 */
public class ArgumentSignature {

    private ImmutableList<TypeExp> argTypes;

    public ArgumentSignature(ImmutableList<TypeExp> argumentTypes) {
        argTypes = argumentTypes;
    }

    public ArgumentSignature(TypeExp argumentType) {
        argTypes = ImmutableList.of(argumentType);
    }

    public ImmutableList<TypeExp> getArgumentTypes() {
        return argTypes;
    }

}
