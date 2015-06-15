// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author: Sebastian Conybeare
 */
public class FADT extends TypeFExp {

    private final ImmutableList<FConstructor> constructors;
    private final FTypeName name;

    public FADT(ImmutableList<FArgumentSignature> argSigs, FTarget target) {
        int size = argSigs.size();
        ArrayList<FConstructor> constructorArr = new ArrayList<FConstructor>(size);

        Iterator<FArgumentSignature> argSigIter = argSigs.iterator();

        for(FConstructor currCon : constructorArr) {
            FConstructorSignature currConSig = new FConstructorSignature(argSigIter.next(), this);
            currCon = new FConstructor(currConSig, target);
        }
        
        constructors = ImmutableList.copyOf(constructorArr);
        name = new FTypeName(target);
    }

    public ImmutableList<FConstructor> getFConstructors() {
        return constructors;
    }

    public FTypeName getName() {
        return name;
    }
    
}
