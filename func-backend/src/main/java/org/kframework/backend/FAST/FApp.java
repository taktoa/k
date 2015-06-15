// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FApp extends FExp {

    private final FExp func, arg;

    public FApp(FExp func, FExp arg, FTarget target) {
        super(target);
        this.func = func;
        this.arg = arg;
    }

    public FExp getFunction() {
        return func;
    }

    public FExp getArgument() {
        return arg;
    }

    public String unparse() {
        return target.unparse(this);
    }
    
}
