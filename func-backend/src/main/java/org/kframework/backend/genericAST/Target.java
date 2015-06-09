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
    public abstract String unparse(Match m);

    public abstract String newConstructorName();
    public abstract String newTypeName();
    public abstract String newVariable();

    public abstract String declare(ADT a);

    // The implementor should have this method return an expression
    // representing a specialized catamorphism which has, for each
    // constructor C with argument type A, an argument of type:
    // ((a -> e) -> A -> e).
    // For example, if the first argument a is the Nat ADT defined by
    // data Nat = Z | S Nat, we have the catamorphism, whose type is:
    // ((Nat -> e) -> e) -> ((Nat -> e) -> Nat -> e) -> (Nat -> e)
    //
    // We recommend that the implementor create a Catamorphism class
    // internal to the specialized Target class in order to accomplish
    // this, with additional unparse and declare methods.
    protected abstract Exp createCatamorphism(ADT a, TypeExp e);

}
