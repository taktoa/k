// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.value.Constructor;
import org.kframework.backend.genericAST.value.Catamorphism;
import org.kframework.backend.genericAST.Target;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private final ImmutableList<Constructor> constructors;
    private final TypeName name;
    private final Catamorphism cata;

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
        cata = new Catamorphism(this, target);
    }

    public ImmutableList<Constructor> getConstructors() {
        return constructors;
    }

    public Catamorphism getCatamorphism() {
        return cata;
    }

    public TypeName getName() {
        return name;
    }
    
}
