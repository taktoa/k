// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public abstract class Exp {

    protected final Unparser unparser;

    public Exp(Unparser unparser) {
        this.unparser = unparser;
    }
    
    public abstract String unparse();
}
