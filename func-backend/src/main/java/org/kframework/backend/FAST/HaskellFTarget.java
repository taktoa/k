// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public abstract class HaskellFTarget extends FTarget {
    private int constructorNameCount;
    private int typeNameCount;
    private int variableNameCount;
    private static int catamorphismNameCount = 0;
    
    public HaskellFTarget() {
        constructorNameCount = 0;
        typeNameCount = 0;
        variableNameCount = 0;
    }

    @Override
    public String unparse(FApp a) {
        return String.format("(%s) (%s)", a.getFunction().unparse(), a.getArgument().unparse());
    }

    @Override
    public String unparse(FConstructor c) {
        return c.getFConstructorName().toString();
    }

    @Override
    public String unparse(FIf i) {
        return String.format("if (%s) then (%s) else (%s)",
                             i.getCondition().unparse(),
                             i.getTrueBranch().unparse(),
                             i.getFalseBranch().unparse());
    }

    @Override
    public String unparse(FLitBool b) {
        return b.getValue() ? "True" : "False";
    }

    @Override
    public String unparse(FLitInt i) {
        return String.format("%i", i.getValue());
    }

    @Override
    public String unparse(FLitString s) {
        return String.format("\"%s\"", s.getValue());
    }

    @Override
    public String unparse(FVariable v) {
        return v.toString();
    }

    // public String unparse(Catamorphism c) {
    //     return c.getCatamorphismName().toString();
    // }
    

    @Override
    public String newFConstructorName() {
        String name = String.format("FConstructor%i",
                                       constructorNameCount);
        constructorNameCount++;
        return name;
    }

    @Override
    public String newFTypeName() {
        String rawName = String.format("Type%i",
                                       typeNameCount);
        typeNameCount++;
        return rawName;
    }

    @Override
    public String newFVariable() {
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
    public String declare(FADT a) {
        return "";
    }

    @Override
    protected FExp createCatamorphism(FADT a, TypeFExp e) {
        return new FVariable(this); //TODO implement polymorphic pattern matching.
    }

    private class Catamorphism extends FExp {
        private final FADT domain;
        private final CatamorphismName name;

        public Catamorphism(FADT domain, FTarget target) {
            super(target);
            this.domain = domain;
            name = new CatamorphismName(target);
        }

        public FADT getDomain() {
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

    private class CatamorphismName extends FIdentifier {
        public CatamorphismName(FTarget target) {
            super(HaskellFTarget.newCatamorphismName());
        }
    }
    
}
