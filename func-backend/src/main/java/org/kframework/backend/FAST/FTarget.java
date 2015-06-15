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

}
