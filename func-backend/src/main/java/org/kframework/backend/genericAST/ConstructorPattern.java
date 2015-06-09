// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class ConstructorPattern extends Pattern {

    protected final Target target;
    private final Constructor constructor;
    private final ImmutableList<Pattern> args;

    public ConstructorPattern(Target target, Constructor constructor, ImmutableList<Pattern> args) {
        super(target);
        this.target = target;
        this.constructor = constructor;
        this.args = args;
    }

    public Constructor getConstructor() {
        return constructor;
    }

    public ImmutableList<Pattern> getArgs() {
        return args;
    }

}
