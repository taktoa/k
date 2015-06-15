// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

import com.google.common.collect.ImmutableList;
/**
 * @author: Sebastian Conybeare
 */
public class FConstructorFPattern extends FPattern {

    protected final FTarget target;
    private final FConstructor constructor;
    private final ImmutableList<FPattern> args;

    public FConstructorFPattern(FTarget target, FConstructor constructor, ImmutableList<FPattern> args) {
        super(target);
        this.target = target;
        this.constructor = constructor;
        this.args = args;
    }

    public FConstructorFPattern(FTarget target, FConstructor constructor, FPattern... args) {
        super(target);
        this.target = target;
        this.constructor = constructor;
        this.args = ImmutableList.copyOf(args);
    }

    public FConstructor getFConstructor() {
        return constructor;
    }

    public ImmutableList<FPattern> getArgs() {
        return args;
    }

}
