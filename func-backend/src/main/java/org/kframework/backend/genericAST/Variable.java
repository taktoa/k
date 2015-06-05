// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class Variable extends Exp {

    private final VariableName name;

    public Variable(Target target) {
        super(target);
        name = new VariableName(target);
    }

    public String getName() {
        return name.toString();
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
