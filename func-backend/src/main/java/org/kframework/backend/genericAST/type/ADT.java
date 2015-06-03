// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.type.TypeExp;
import org.kframework.backend.genericAST.type.ConstructorSignature;
import org.kframework.backend.genericAST.value.Constructor;

import com.google.common.collect.ImmutableList;

import java.util.Vector;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private ImmutableList<ConstructorSignature> constructors;

    public ADT(ImmutableList<ArgumentSignature> signatures) {
        int size = signatures.size();
        ConstructorSignature[] csigs = new ConstructorSignature[size];
        
        for(int i = 0; i < size; i++) {
            csigs[i] = new ConstructorSignature(signatures.get(i), this);
        }

        constructors = ImmutableList.copyOf(csigs);
        
    }
    
}
