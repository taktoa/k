// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class HaskellTarget extends Target {
    private int constructorNameCount;
    private int typeNameCount;
    private int variableNameCount;
    private static int catamorphismNameCount = 0;
    
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
        return c.getConstructorName().toString();
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
        return v.toString();
    }

    // public String unparse(Catamorphism c) {
    //     return c.getCatamorphismName().toString();
    // }
    

    @Override
    public String newConstructorName() {
        String name = String.format("Constructor%i",
                                       constructorNameCount);
        constructorNameCount++;
        return name;
    }

    @Override
    public String newTypeName() {
        String rawName = String.format("Type%i",
                                       typeNameCount);
        typeNameCount++;
        return rawName;
    }

    @Override
    public String newVariable() {
        String rawName = String.format("var%i",
                                       variableNameCount);
        variableNameCount++;
        return rawName;
    }

    static String newCatamorphismName() {
        String rawName = String.format("cata%d",
                                       catamorphismNameCount);
        catamorphismNameCount++;
        return rawName;
    }

    @Override
    public String declare(ADT a) {
        return "";
    }

    @Override
    protected Exp createCatamorphism(ADT a, TypeExp e) {
        return new Variable(this); //TODO implement polymorphic pattern matching.
    }

    private class Catamorphism extends Exp {
        private final ADT domain;
        private final CatamorphismName name;

        public Catamorphism(ADT domain, Target target) {
            super(target);
            this.domain = domain;
            name = new CatamorphismName(target);
        }

        public ADT getDomain() {
            return domain;
        }

        public CatamorphismName getCatamorphismName() {
            return name;
        }

        @Override
        public String unparse() {
            return name.toString();
        }
    }

    private class CatamorphismName extends Identifier {
        public CatamorphismName(Target target) {
            super(HaskellTarget.newCatamorphismName());
        }
    }
    
}
