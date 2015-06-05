// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class ConstructorSignature {

    private ArgumentSignature argTypes;
    private TypeExp retType;

    public ConstructorSignature(ArgumentSignature argumentTypes, TypeExp returnType) {
        argTypes = argumentTypes;
        retType = returnType;
    }

    public ConstructorSignature(ImmutableList<TypeExp> argumentTypes, TypeExp returnType) {
        argTypes = new ArgumentSignature(argumentTypes);
        retType = returnType;
    }

    public ConstructorSignature(TypeExp argumentType) {
        argTypes = new ArgumentSignature(argumentType);
    }

    public ArgumentSignature getArgumentSignature() {
        return argTypes;
    }

    public ImmutableList<TypeExp> getArgumentTypes() {
        return argTypes.getArgumentTypes();
    }

    public TypeExp getReturnType() {
        return retType;
    }
    
}
