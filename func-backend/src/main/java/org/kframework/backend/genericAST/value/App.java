// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.value.Exp;

/**
 * @author: Sebastian Conybeare
 */
public abstract class App extends Exp {

    private Exp func, arg;

    public App(Exp f, Exp x) {
        func = f;
        arg = x;
    }

    public Exp getFunction() {
        return func;
    }

    public Exp getArgument() {
        return arg;
    }
    
}
