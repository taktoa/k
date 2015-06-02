// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.type.TypeExp;
import org.kframework.backend.genericAST.type.ConstructorSignature;
import org.kframework.backend.genericAST.value.Constructor;
import com.google.common.collect.ImmutableList;

/**
 * @author: Sebastian Conybeare
 */
public class ADT extends TypeExp {

    private ImmutableList<Constructor> cases;

    // @TODO create (java) constructors that do not allow for
    // inconsistent return types of the constructors.  
    public ADT(ImmutableList<Constructor> constructors) {
        cases = constructors;
    }
    
}
