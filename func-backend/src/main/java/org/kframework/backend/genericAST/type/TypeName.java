// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.type;

import org.kframework.backend.genericAST.Target;
import org.kframework.backend.genericAST.Identifier;
/**
 * @author: Sebastian Conybeare
 */
public class TypeName extends Identifier {

    public TypeName(Target target) {
        super(target.newTypeName());
    }

}
