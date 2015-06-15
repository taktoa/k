// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class FConstructorSignature {

    private final FArgumentSignature argTypes;
    private TypeFExp retType;

    public FConstructorSignature(FArgumentSignature argumentTypes, TypeFExp returnType) {
        argTypes = argumentTypes;
        retType = returnType;
    }

    public FConstructorSignature(ImmutableList<TypeFExp> argumentTypes, TypeFExp returnType) {
        argTypes = new FArgumentSignature(argumentTypes);
        retType = returnType;
    }

    public FConstructorSignature(TypeFExp argumentType) {
        argTypes = new FArgumentSignature(argumentType);
    }

    public FArgumentSignature getFArgumentSignature() {
        return argTypes;
    }

    public ImmutableList<TypeFExp> getArgumentTypes() {
        return argTypes.getArgumentTypes();
    }

    public TypeFExp getReturnType() {
        return retType;
    }
    
}
