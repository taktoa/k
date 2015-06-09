// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class PatternBinding extends GenericASTNode {

    private final Pattern lhs;
    private final Exp rhs;

    public PatternBinding(Target target, Pattern lhs, Exp rhs) {
        super(target);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public Pattern getLHS() {
        return lhs;
    }

    public Exp getRHS() {
        return rhs;
    }

}
