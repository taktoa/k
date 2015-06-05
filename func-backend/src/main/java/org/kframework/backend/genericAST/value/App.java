// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST.value;

import org.kframework.backend.genericAST.Unparser;
/**
 * @author: Sebastian Conybeare
 */
public class App extends Exp {

    private final Exp func, arg;

    public App(Exp func, Exp arg, Unparser unparser) {
        super(unparser);
        this.func = func;
        this.arg = arg;
    }

    public Exp getFunction() {
        return func;
    }

    public Exp getArgument() {
        return arg;
    }

    public String unparse() {
        return unparser.unparse(this);
    }
    
}
