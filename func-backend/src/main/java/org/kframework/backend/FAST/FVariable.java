// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FVariable extends FExp {

    private final FVariableName name;

    public FVariable(FTarget target) {
        super(target);
        name = new FVariableName(target);
    }

    public String getName() {
        return name.toString();
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
