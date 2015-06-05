// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class Catamorphism extends Exp {

    private final ADT domainType;
    private final CatamorphismName name;

    public Catamorphism(ADT domainType, Target target) {
        super(target);
        this.domainType = domainType;
        name = new CatamorphismName(target);
    }

    public ADT getDomainType() {
        return domainType;
    }

    public CatamorphismName getCatamorphismName() {
        return name;
    }

    @Override
    public String unparse() {
        return target.unparse(this);
    }

}
