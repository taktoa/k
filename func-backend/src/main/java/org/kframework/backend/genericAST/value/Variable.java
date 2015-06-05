// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class Variable extends Exp {

    private final String name;

    public Variable(String name, Unparser unparser) {
        super(unparser);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String unparse() {
        return unparser.unparse(this);
    }

}
