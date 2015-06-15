// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

import com.google.common.collect.ImmutableList;

/**
 * @author: Sebastian Conybeare
 */
public class FArgumentSignature {

    private final ImmutableList<TypeFExp> argTypes;

    public FArgumentSignature(ImmutableList<TypeFExp> argumentTypes) {
        argTypes = argumentTypes;
    }

    public FArgumentSignature(TypeFExp argumentType) {
        argTypes = ImmutableList.of(argumentType);
    }

    public ImmutableList<TypeFExp> getArgumentTypes() {
        return argTypes;
    }

}
