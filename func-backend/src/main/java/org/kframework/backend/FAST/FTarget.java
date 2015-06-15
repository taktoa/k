// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class FTarget {

    public abstract String unparse(FApp a);
    public abstract String unparse(FConstructor c);
    public abstract String unparse(FIf i);
    public abstract String unparse(FLitBool b);
    public abstract String unparse(FLitInt i);
    public abstract String unparse(FLitString s);
    public abstract String unparse(FVariable v);
    public abstract String unparse(FMatch m);

    public abstract String newFConstructorName();
    public abstract String newFTypeName();
    public abstract String newFVariable();

    public abstract String declare(FDeclarable a);

    // The implementor should have this method return an expression
    // representing a specialized catamorphism which has, for each
    // constructor C with argument type A, an argument of type:
    // ((a -> e) -> A -> e).
    // For example, if the first argument a is the Nat FADT defined by
    // data Nat = Z | S Nat, we have the catamorphism, whose type is:
    // ((Nat -> e) -> e) -> ((Nat -> e) -> Nat -> e) -> (Nat -> e)
    //
    // We recommend that the implementor create a Catamorphism class
    // internal to the specialized FTarget class in order to accomplish
    // this, with additional unparse and declare methods.
    // protected abstract FExp createCatamorphism(FADT a, TypeFExp e);

}
