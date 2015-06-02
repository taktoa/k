// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.type.TypeExp;
import com.google.common.collect.ImmutableList;

/**
 * @author: Sebastian Conybeare
 */
public class ConstructorSignature {

    private ImmutableList<TypeExp> argTypes;
    private TypeExp retType;

    public ConstructorSignature(ImmutableList<TypeExp> argumentTypes, TypeExp returnType) {
        argTypes = argumentTypes;
        retType = returnType;
    }

    public ConstructorSignature(TypeExp argumentType) {
        argTypes = ImmutableList.of(argumentType);
    }

    public ImmutableList<TypeExp> getArgumentTypes() {
        return argTypes;
    }

    public TypeExp getReturnType() {
        return retType;
    }
    
}
