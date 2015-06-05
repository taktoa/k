// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class LitBool extends Exp {

    private final boolean val;

    public LitBool(boolean b, Unparser unparser) {
        super(unparser);
        val = b;
    }

    public boolean getValue() {
        return val;
    }

    @Override
    public String unparse() {
        return unparser.unparse(this);
    }


}
