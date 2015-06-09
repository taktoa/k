// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class VarPattern extends Pattern {

    private final Variable var;

    public VarPattern(Target target) {
        super(target);
        var = new Variable(target);
    }

    public Variable getVariable() {
        return var;
    }

}
