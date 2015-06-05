// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private final ImmutableList<Constructor> constructors;
    private final TypeName name;

    public ADT(ImmutableList<ArgumentSignature> argSigs, Target target) {
        int size = argSigs.size();
        ArrayList<Constructor> constructorArr = new ArrayList<Constructor>(size);

        Iterator<ArgumentSignature> argSigIter = argSigs.iterator();

        for(Constructor currCon : constructorArr) {
            ConstructorSignature currConSig = new ConstructorSignature(argSigIter.next(), this);
            currCon = new Constructor(currConSig, target);
        }
        
        constructors = ImmutableList.copyOf(constructorArr);
        name = new TypeName(target);
    }

    public ImmutableList<Constructor> getConstructors() {
        return constructors;
    }

    public TypeName getName() {
        return name;
    }
    
}
