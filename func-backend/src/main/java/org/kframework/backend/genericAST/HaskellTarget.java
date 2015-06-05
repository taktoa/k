// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

import org.kframework.backend.genericAST.value.App;
import org.kframework.backend.genericAST.value.Constructor;
import org.kframework.backend.genericAST.value.If;
import org.kframework.backend.genericAST.value.LitBool;
import org.kframework.backend.genericAST.value.LitInt;
import org.kframework.backend.genericAST.value.LitString;
import org.kframework.backend.genericAST.value.Variable;
import org.kframework.backend.genericAST.type.TypeName;
import org.kframework.backend.genericAST.ConstructorName;
//import org.kframework.backend.genericAST.type.ADT;

/**
 * @author: Sebastian Conybeare
 */
public class HaskellTarget extends Target {
    private int constructorNameCount;
    private int typeNameCount;
    private int variableNameCount;
    public HaskellTarget() {
        constructorNameCount = 0;
        typeNameCount = 0;
        variableNameCount = 0;
    }

    @Override
    public String unparse(App a) {
        return String.format("(%s) (%s)", a.getFunction().unparse(), a.getArgument().unparse());
    }

    @Override
    public String unparse(Constructor c) {
        return c.getConstructorName().getName();
    }

    @Override
    public String unparse(If i) {
        return String.format("if (%s) then (%s) else (%s)",
                             i.getCondition().unparse(),
                             i.getTrueBranch().unparse(),
                             i.getFalseBranch().unparse());
    }

    @Override
    public String unparse(LitBool b) {
        return b.getValue() ? "True" : "False";
    }

    @Override
    public String unparse(LitInt i) {
        return String.format("%i", i.getValue());
    }

    @Override
    public String unparse(LitString s) {
        return String.format("\"%s\"", s.getValue());
    }

    @Override
    public String unparse(Variable v) {
        return v.getName();
    }

    // @Override
    // public String unparse(ADT a) {
        
    // }

    @Override
    public ConstructorName newConstructorName() {
        String rawName = String.format("Constructor%i",
                                       constructorNameCount);
        constructorNameCount++;
        return new ConstructorName(rawName);
    }

    @Override
    public TypeName newTypeName() {
        String rawName = String.format("Type%i",
                                       typeNameCount);
        typeNameCount++;
        return new TypeName(rawName);
    }

    @Override
    public Variable newVariable() {
        String rawName = String.format("var%i",
                                       variableNameCount);
        variableNameCount++;
        return new Variable(rawName, this);
    }
    
}
