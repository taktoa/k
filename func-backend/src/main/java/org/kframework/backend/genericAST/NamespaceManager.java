// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import org.kframework.backend.genericAST.ConstructorName;
import org.kframework.backend.genericAST.type.TypeName;
/**
 * @author: Sebastian Conybeare
 */
public abstract class NamespaceManager {
    
    public abstract ConstructorName newConstructorName();
    public abstract TypeName newTypeName();

}