// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.value.Constructor;

import org.kframework.backend.genericAST.NamespaceManager;
import org.kframework.backend.genericAST.Unparser;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private final ImmutableList<Constructor> constructors;
    private final TypeName name;

    public ADT(ImmutableList<ArgumentSignature> argSigs, NamespaceManager nm, Unparser unparser) {
        int size = argSigs.size();
        ArrayList<Constructor> constructorArr = new ArrayList<Constructor>(size);

        Iterator<ArgumentSignature> argSigIter = argSigs.iterator();

        for(Constructor currCon : constructorArr) {
            ConstructorSignature currConSig = new ConstructorSignature(argSigIter.next(), this);
            currCon = new Constructor(nm, currConSig, unparser);
        }
        
        constructors = ImmutableList.copyOf(constructorArr);
        name = nm.newTypeName();
        
    }

    public ImmutableList<Constructor> getConstructors() {
        return constructors;
    }

    public TypeName getName() {
        return name;
    }
    
}
