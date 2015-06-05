// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import org.kframework.backend.genericAST.value.App;
import org.kframework.backend.genericAST.value.Constructor;
import org.kframework.backend.genericAST.value.If;
import org.kframework.backend.genericAST.value.LitBool;
import org.kframework.backend.genericAST.value.LitInt;
import org.kframework.backend.genericAST.value.LitString;
import org.kframework.backend.genericAST.value.Variable;
//import org.kframework.backend.genericAST.type.ADT;

import org.kframework.backend.genericAST.type.TypeName;
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

    public abstract ConstructorName newConstructorName();
    public abstract TypeName newTypeName();
    public abstract Variable newVariable();

}
