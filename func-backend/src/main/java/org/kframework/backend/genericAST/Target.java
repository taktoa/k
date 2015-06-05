// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class Target {

    public abstract String unparse(App a);
    public abstract String unparse(Constructor c);
    public abstract String unparse(If i);
    public abstract String unparse(LitBool b);
    public abstract String unparse(LitInt i);
    public abstract String unparse(LitString s);
    public abstract String unparse(Variable v);
    public abstract String unparse(Catamorphism c);

    public abstract String newConstructorName();
    public abstract String newTypeName();
    public abstract String newVariable();
    public abstract String newCatamorphismName();

    public abstract String declare(ADT a);

}
