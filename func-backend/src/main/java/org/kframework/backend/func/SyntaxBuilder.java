package org.kframework.backend.func;

import java.util.List;

/**
 * @author: Remy Goldschmidt
 */
public class SyntaxBuilder {
    private final StringBuilder sb;

    public SyntaxBuilder(StringBuilder s) {
        sb = s;
    }

    public SyntaxBuilder() {
        sb = new StringBuilder();
    }

    public void append(String s) {
        sb.append(s);
    }

    public void appendf(String format, Object... args) {
        append(String.format(format, args));
    }

    public StringBuilder getStringBuilder() {
        return sb;
    }

    public String render() {
        return sb.toString();
    }

    @Override
    public String toString() {
        return render();
    }

    public void addGlobalLet(String name, String value) {
        beginLetExpression();
        beginLetDefinitions();
        addLetEquation(name, value);
        endLetDefinitions();
        endLetExpression();
    }

    public void addLetEquation(String name, String value) {
        beginLetEquation();
        addLetEquationName(name);
        addLetEquationValue(value);
        endLetEquation();
    }

    public void addLetScope(String scope) {
        beginLetScope();
        append(scope);
        endLetScope();
    }

    public void addLetEquationName(String name) {
        beginLetEquationName();
        append(name);
        endLetEquationName();
    }

    public void addLetEquationValue(String value) {
        beginLetEquationValue();
        append(value);
        endLetEquationValue();
    }

    public void beginLetEquation() {
        // Begin let equation
    }

    public void endLetEquation() {
        // End let equation
    }

    public void beginLetEquationName() {
        // Begin let equation name
    }

    public void endLetEquationName() {
        append(" = ");
    }

    public void beginLetEquationValue() {
        // Begin let equation value
    }

    public void endLetEquationValue() {
        // End let equation value
    }

    public void addLetEquationSeparator() {
        addNewline();
        append(" and ");
    }

    public void beginLetDefinitions() {
        // Begin let definitions
    }

    public void endLetDefinitions() {
        // End let definitions
    }

    public void beginLetScope() {
        append(" in ");
    }

    public void endLetScope() {
        // End let scope
    }

    public void beginLetExpression() {
        append("let ");
    }

    public void endLetExpression() {
        // End let expression
    }



    public void addLetrecEquation(String name, String value) {
        beginLetrecEquation();
        addLetrecEquationName(name);
        addLetrecEquationValue(value);
        endLetrecEquation();
    }

    public void addLetrecEquationName(String name) {
        beginLetrecEquationName();
        append(name);
        endLetrecEquationName();
    }

    public void addLetrecEquationValue(String value) {
        beginLetrecEquationValue();
        append(value);
        endLetrecEquationValue();
    }

    public void beginLetrecEquation() {
        // Begin letrec equation
    }

    public void endLetrecEquation() {
        addNewline();
    }

    public void beginLetrecEquationName() {
        // Begin letrec equation name
    }

    public void endLetrecEquationName() {
        append(" = ");
    }

    public void beginLetrecEquationValue() {
        // Begin letrec equation value
    }

    public void endLetrecEquationValue() {
        // End letrec equation value
    }

    public void addLetrecEquationSeparator() {
        addNewline();
        append(" and ");
    }

    public void beginLetrecDefinitions() {
        // Begin letrec definitions
    }

    public void endLetrecDefinitions() {
        // End letrec definitions
    }

    public void beginLetrecScope() {
        append(" in ");
    }

    public void endLetrecScope() {
        // End letrec scope
    }

    public void beginLetrecExpression() {
        append("let rec ");
    }

    public void endLetrecExpression() {
        // End letrec expression
    }




    public void addMatch(String value, List<String> pats, List<String> vals) {
        beginMatchExpression(value);
        for(int i = Math.min(pats.size(), vals.size()) - 1; i >= 0; i--) {
            addMatchEquation(pats.get(i), vals.get(i));
        }
        endMatchExpression();
    }

    public void addMatchEquation(String pattern, String value) {
        beginMatchEquation();
        addMatchEquationPattern(pattern);
        addMatchEquationValue(value);
        endMatchEquation();
    }

    public void addMatchEquation(String equation) {
        beginMatchEquation();
        append(equation);
        endMatchEquation();
    }

    public void addMatchEquationPattern(String pattern) {
        beginMatchEquationPattern();
        append(pattern);
        endMatchEquationPattern();
    }

    public void addMatchEquationValue(String value) {
        beginMatchEquationValue();
        append(value);
        endMatchEquationValue();
    }

    public void beginMatchExpression(String varname) {
        append("match ");
        append(varname);
        append(" with ");
        addNewline();
    }

    public void endMatchExpression() {
        // End match expression
    }

    public void beginMatchEquation() {
        append("|");
    }

    public void endMatchEquation() {
        addNewline();
    }

    public void beginMatchEquationPattern() {
        // Begin match equation pattern
    }

    public void endMatchEquationPattern() {
        append(" -> ");
    }

    public void beginMatchEquationValue() {
        // Begin match equation value
    }

    public void endMatchEquationValue() {
        // End match equation value
    }




    public void beginMultilineComment() {
        append("(*");
    }

    public void endMultilineComment() {
        append("*)");
    }

    public void addNewline() {
        append("\n");
    }

    public void addImport(String i) {
        append("open ");
        append(i);
        addNewline();
    }




    public void beginTypeDefinition(String typename) {
        append("type ");
        append(typename);
        append(" = ");
        addNewline();
    }

    public void endTypeDefinition() {
        // End type definition
    }

    public void addConstructor(String con) {
        beginConstructor();
        append(con);
        endConstructor();
    }

    public void addConstructorSum() {
        append("|");
    }

    public void beginConstructor() {
        append("|");
    }

    public void endConstructor() {
        addNewline();
    }

    public void beginConstructorName() {
        // Begin constructor name
    }

    public void endConstructorName() {
        // End constructor name
    }

    public void beginConstructorArgs() {
        append(" of ");
    }

    public void endConstructorArgs() {
        // End constructor args
    }

    public void addType(String typename) {
        append(typename);
    }

    public void addTypeProduct() {
        append(" * ");
    }
}
