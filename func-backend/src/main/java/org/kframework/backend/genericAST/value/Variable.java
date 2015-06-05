// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Target;
/**
 * @author: Sebastian Conybeare
 */
public class Variable extends Exp {

    private final String name;

    public Variable(String name, Target target) {
        super(target);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
