// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.type.TypeExp;
import org.kframework.backend.genericAST.type.ConstructorSignature;
import org.kframework.backend.genericAST.value.Constructor;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private ImmutableList<ConstructorSignature> constructorSignatures;

    public ADT(ImmutableList<ArgumentSignature> argSigs) {
        int size = argSigs.size();
        ArrayList<ConstructorSignature> conSigs = new ArrayList<ConstructorSignature>(size);

        Iterator<ArgumentSignature> argSigIter = argSigs.iterator();

        for(ConstructorSignature conSig : conSigs) {
            conSig = new ConstructorSignature(argSigIter.next(), this);
        }
        
        constructorSignatures = ImmutableList.copyOf(conSigs);
        
    }
    
}
