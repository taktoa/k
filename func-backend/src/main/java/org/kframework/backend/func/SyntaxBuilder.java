// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import static org.kframework.backend.func.FuncUtil.*;


/**
 * This class is a temporary way to make the current
 * functional backend work with code adapted from the
 * OCaml backend
 *
 * @author Remy Goldschmidt
 */
public class SyntaxBuilder implements Cloneable {
    private final List<Syntax> stx;
    private int parens = 0;
    private int linum = 0;
    private static final Pattern isSpace      = Pattern.compile("\\s+");
    private static final Pattern isNewline    = Pattern.compile("\n");
    private static final Pattern isOpenParen  = Pattern.compile("\\(");
    private static final Pattern isCloseParen = Pattern.compile("\\)");

    private SyntaxBuilder(List<Syntax> stx) {
        this.stx = stx;
    }

    public SyntaxBuilder() {
        this(Lists.newArrayList());
    }

    public SyntaxBuilder(String s) {
        this();
        append(s);
    }

    public SyntaxBuilder(SyntaxBuilder sb) {
        this();
        append(sb);
    }

    public SyntaxBuilder(String... strings) {
        this();
        for(String s : strings) {
            append(s);
        }
    }

    private List<Syntax> getStx() {
        return stx;
    }

    public int getNumParens() {
        return parens;
    }

    public int getLineNum() {
        return linum;
    }



    public SyntaxBuilder append(Syntax s) {
        stx.add(s);
        if("(".equals(s.getString())) {
            parens++;
        } else if(s.getString().contains("(")) {
            Matcher m = isOpenParen.matcher(s.getString());
            while(m.find()) { parens++; }
        }

        if(")".equals(s.getString())) {
            parens--;
        } else if(s.getString().contains(")")) {
            Matcher m = isCloseParen.matcher(s.getString());
            while(m.find()) { parens--; }
        }

        if("\n".equals(s.getString())) {
            linum++;
        } else if(s.getString().contains("\n")) {
            Matcher m = isNewline.matcher(s.getString());
            while(m.find()) { linum++; }
        }
        // if(between(linum, 26900, 27000)) {
        //     addStackTrace();
        // }
        return this;
    }

    public SyntaxBuilder append(String s) {
        return append(new SyntaxString(s));
    }

    public SyntaxBuilder append(SyntaxBuilder sb) {
        for(Syntax s : sb.getStx()) {
            append(s.clone());
        }
        return this;
    }

    public SyntaxBuilder appendf(String format, Object... args) {
        return append(String.format(format, args));
    }




    public String render() {
        StringBuilder sb = new StringBuilder();
        for(Syntax s : stx) {
            sb.append(s.getString());
        }
        return sb.toString();
    }

    public List<String> pretty() {
        List<String> ret = Lists.newArrayListWithCapacity(stx.size() + 1);
        for(Syntax s : stx) {
            ret.add(s.toString());
        }
        return ret;
    }

    @Override
    public SyntaxBuilder clone() {
        SyntaxBuilder res = newsb();
        for(Syntax s : stx) {
            res.append(s.clone());
        }
        return res;
    }

    @Override
    public String toString() {
        return render();
    }




    public SyntaxBuilder addKeyword(Object keyword) {
        append(new SyntaxKeyword(keyword.toString()));
        return this;
    }

    public SyntaxBuilder addValue(Object value) {
        append(new SyntaxValue(value.toString()));
        return this;
    }

    public SyntaxBuilder addSpace() {
        addKeyword(" ");
        return this;
    }

    public SyntaxBuilder addStackTrace() {
        stx.add(new SyntaxString("\n"));
        stx.add(new SyntaxString("(* "));
        stx.add(new SyntaxString("DEBUG:\n"));
        stx.add(new SyntaxString(StringUtils.join(Thread.currentThread()
                                                        .getStackTrace(),
                                                  "\n")));
        stx.add(new SyntaxString(" *)"));
        stx.add(new SyntaxString("\n"));
        return this;
    }

    public SyntaxBuilder addNewline() {
        addKeyword("\n");
        return this;
    }

    public SyntaxBuilder stripSurroundingSpaces() {
        return stripSpaceBefore().stripSpaceAfter();
    }

    public SyntaxBuilder stripSpaceBefore() {
        synchronized(stx) {
            while(isSpace.matcher(stx.get(0).getString()).matches()) {
                stx.remove(0);
            }
            stx.get(0).stripSpaceBefore();
        }
        return this;
    }

    public SyntaxBuilder stripSpaceAfter() {
        synchronized(stx) {
            int size = stx.size();
            while(isSpace.matcher(stx.get(size - 1).getString()).matches()) {
                stx.remove(size - 1);
                size = stx.size();
            }
            stx.get(stx.size() - 1).stripSpaceAfter();
        }
        return this;
    }

    public SyntaxBuilder removeNewlines() {
        synchronized(stx) {
            for(int i = 0; stx.size() > i; i++) {
                stx.get(i).removeNewlines();
            }
        }
        linum = 0;
        return this;
    }

    public SyntaxBuilder beginMultilineComment() {
        return addKeyword("(*");
    }

    public SyntaxBuilder endMultilineComment() {
        return addKeyword("*)");
    }

    public SyntaxBuilder addImport(String i) {
        addKeyword("open");
        addSpace();
        addValue(i);
        addNewline();
        return this;
    }





    public SyntaxBuilder addApplication(String fnName,
                                        SyntaxBuilder... args) {
        beginApplication();
        addFunction(fnName);
        for(SyntaxBuilder a : args) {
            addArgument(a);
        }
        endApplication();
        return this;
    }

    public SyntaxBuilder beginApplication() {
        return beginParenthesis();
    }

    public SyntaxBuilder endApplication() {
        return endParenthesis();
    }

    public SyntaxBuilder addFunction(String fnName) {
        return addValue(fnName);
    }

    public SyntaxBuilder addArgument(SyntaxBuilder arg) {
        beginArgument();
        append(arg);
        endArgument();
        return this;
    }

    public SyntaxBuilder beginArgument() {
        addSpace();
        beginParenthesis();
        return this;
    }

    public SyntaxBuilder endArgument() {
        return endParenthesis();
    }

    public SyntaxBuilder beginLambda(String... vars) {
        beginParenthesis();
        addKeyword("fun");
        for(String v : vars) {
            addSpace();
            addValue(v);
        }
        addSpace();
        addKeyword("->");
        addSpace();
        return this;
    }

    public SyntaxBuilder endLambda() {
        return endParenthesis();
    }

    public SyntaxBuilder addEqualityTest(SyntaxBuilder a,
                                         SyntaxBuilder b) {
        addSpace();
        beginParenthesis();
        beginParenthesis();
        append(a.removeNewlines());
        endParenthesis();
        addSpace();
        addKeyword("=");
        addSpace();
        beginParenthesis();
        append(b.removeNewlines());
        endParenthesis();
        endParenthesis();
        addSpace();
        return this;
    }

    public SyntaxBuilder addConditionalIf() {
        addSpace();
        addKeyword("if");
        addSpace();
        return this;
    }

    public SyntaxBuilder addConditionalThen() {
        addSpace();
        addKeyword("then");
        addSpace();
        return this;
    }

    public SyntaxBuilder addConditionalElse() {
        addSpace();
        addKeyword("else");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginParenthesis() {
        addKeyword("(");
        return this;
    }

    public SyntaxBuilder endParenthesis() {
        addKeyword(")");
        return this;
    }



    public SyntaxBuilder addGlobalLet(SyntaxBuilder name,
                                      SyntaxBuilder value) {
        beginLetExpression();
        beginLetDefinitions();
        addLetEquation(name, value);
        endLetDefinitions();
        endLetExpression();
        return this;
    }

    public SyntaxBuilder addLetEquation(SyntaxBuilder name,
                                        SyntaxBuilder value) {
        beginLetEquation();
        addLetEquationName(name);
        addLetEquationValue(value);
        endLetEquation();
        return this;
    }

    public SyntaxBuilder addLetScope(SyntaxBuilder scope) {
        beginLetScope();
        append(scope);
        endLetScope();
        return this;
    }

    public SyntaxBuilder addLetEquationName(SyntaxBuilder name) {
        beginLetEquationName();
        append(name);
        endLetEquationName();
        return this;
    }

    public SyntaxBuilder addLetEquationValue(SyntaxBuilder value) {
        beginLetEquationValue();
        append(value);
        endLetEquationValue();
        return this;
    }

    public SyntaxBuilder beginLetEquation() {
        // Begin let equation
        return this;
    }

    public SyntaxBuilder endLetEquation() {
        // End let equation
        return this;
    }

    public SyntaxBuilder beginLetEquationName() {
        // Begin let equation name
        return this;
    }

    public SyntaxBuilder endLetEquationName() {
        addSpace();
        addKeyword("=");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginLetEquationValue() {
        // Begin let equation value
        return this;
    }

    public SyntaxBuilder endLetEquationValue() {
        // End let equation value
        return this;
    }

    public SyntaxBuilder addLetEquationSeparator() {
        addNewline();
        addSpace();
        addKeyword("and");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginLetDefinitions() {
        // Begin let definitions
        return this;
    }

    public SyntaxBuilder endLetDefinitions() {
        // End let definitions
        return this;
    }

    public SyntaxBuilder beginLetScope() {
        addSpace();
        addKeyword("in");
        addSpace();
        return this;
    }

    public SyntaxBuilder endLetScope() {
        // End let scope
        return this;
    }

    public SyntaxBuilder beginLetExpression() {
        addKeyword("let");
        addSpace();
        return this;
    }

    public SyntaxBuilder endLetExpression() {
        // End let expression
        return this;
    }



    public SyntaxBuilder addLetrecEquation(SyntaxBuilder name,
                                  SyntaxBuilder value) {
        beginLetrecEquation();
        addLetrecEquationName(name);
        addLetrecEquationValue(value);
        endLetrecEquation();
        return this;
    }

    public SyntaxBuilder addLetrecEquationName(SyntaxBuilder name) {
        beginLetrecEquationName();
        append(name);
        endLetrecEquationName();
        return this;
    }

    public SyntaxBuilder addLetrecEquationValue(SyntaxBuilder value) {
        beginLetrecEquationValue();
        append(value);
        endLetrecEquationValue();
        return this;
    }

    public SyntaxBuilder beginLetrecEquation() {
        // Begin letrec equation
        return this;
    }

    public SyntaxBuilder endLetrecEquation() {
        addNewline();
        return this;
    }

    public SyntaxBuilder beginLetrecEquationName() {
        // Begin letrec equation name
        return this;
    }

    public SyntaxBuilder endLetrecEquationName() {
        addSpace();
        addKeyword("=");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginLetrecEquationValue() {
        // Begin letrec equation value
        return this;
    }

    public SyntaxBuilder endLetrecEquationValue() {
        // End letrec equation value
        return this;
    }

    public SyntaxBuilder addLetrecEquationSeparator() {
        addNewline();
        addSpace();
        addKeyword("and");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginLetrecDefinitions() {
        // Begin letrec definitions
        return this;
    }

    public SyntaxBuilder endLetrecDefinitions() {
        // End letrec definitions
        return this;
    }

    public SyntaxBuilder beginLetrecScope() {
        addSpace();
        addKeyword("in");
        addSpace();
        return this;
    }

    public SyntaxBuilder endLetrecScope() {
        // End letrec scope
        return this;
    }

    public SyntaxBuilder beginLetrecExpression() {
        addKeyword("let rec");
        addSpace();
        return this;
    }

    public SyntaxBuilder endLetrecExpression() {
        // End letrec expression
        return this;
    }




    public SyntaxBuilder addMatch(SyntaxBuilder value,
                                  List<String> pats,
                                  List<String> vals) {
        beginMatchExpression(value);
        int size = Math.min(pats.size(), vals.size());
        for(int i = 0; size > i; i++) {
            addMatchEquation(newsb(pats.get(i)),
                             newsb(vals.get(i)));
        }
        endMatchExpression();
        return this;
    }

    public SyntaxBuilder addMatchEquation(SyntaxBuilder pattern,
                                          SyntaxBuilder value) {
        beginMatchEquation();
        addMatchEquationPattern(pattern);
        addMatchEquationValue(value);
        endMatchEquation();
        return this;
    }

    public SyntaxBuilder addMatchEquation(SyntaxBuilder equation) {
        beginMatchEquation();
        append(equation);
        endMatchEquation();
        return this;
    }

    public SyntaxBuilder addMatchEquationPattern(SyntaxBuilder pattern) {
        beginMatchEquationPattern();
        append(pattern);
        endMatchEquationPattern();
        return this;
    }

    public SyntaxBuilder addMatchEquationValue(SyntaxBuilder value) {
        beginMatchEquationValue();
        append(value);
        endMatchEquationValue();
        return this;
    }

    public SyntaxBuilder beginMatchExpression(SyntaxBuilder varname) {
        beginParenthesis();
        addKeyword("match");
        addSpace();
        append(varname);
        addSpace();
        addKeyword("with");
        addSpace();
        return this;
    }

    public SyntaxBuilder endMatchExpression() {
        endParenthesis();
        addNewline();
        return this;
    }

    public SyntaxBuilder beginMatchEquation() {
        addNewline();
        addKeyword("|");
        addSpace();
        return this;
    }

    public SyntaxBuilder endMatchEquation() {
        addNewline();
        return this;
    }

    public SyntaxBuilder beginMatchEquationPattern() {
        // Begin match equation pattern
        return this;
    }

    public SyntaxBuilder endMatchEquationPattern() {
        addSpace();
        addKeyword("->");
        addSpace();
        return this;
    }

    public SyntaxBuilder beginMatchEquationValue() {
        // Begin match equation value
        return this;
    }

    public SyntaxBuilder endMatchEquationValue() {
        // End match equation value
        return this;
    }







    public SyntaxBuilder beginTypeDefinition(String typename) {
        addKeyword("type");
        addSpace();
        addValue(typename);
        addSpace();
        addKeyword("=");
        addSpace();
        addNewline();
        return this;
    }

    public SyntaxBuilder endTypeDefinition() {
        // End type definition
        return this;
    }

    public SyntaxBuilder addConstructor(SyntaxBuilder con) {
        beginConstructor();
        append(con);
        endConstructor();
        return this;
    }

    public SyntaxBuilder addConstructorName(String con) {
        beginConstructorName();
        addValue(con);
        endConstructorName();
        return this;
    }

    public SyntaxBuilder beginConstructor() {
        addKeyword("|");
        addSpace();
        return this;
    }

    public SyntaxBuilder endConstructor() {
        addNewline();
        return this;
    }

    public SyntaxBuilder beginConstructorName() {
        // Begin constructor name
        return this;
    }

    public SyntaxBuilder endConstructorName() {
        // End constructor name
        return this;
    }

    public SyntaxBuilder beginConstructorArgs() {
        addSpace();
        addKeyword("of");
        addSpace();
        return this;
    }

    public SyntaxBuilder endConstructorArgs() {
        // End constructor args
        return this;
    }

    public SyntaxBuilder addType(SyntaxBuilder typename) {
        append(typename);
        return this;
    }

    public SyntaxBuilder addTypeProduct() {
        addSpace();
        addKeyword("*");
        addSpace();
        return this;
    }











    private class Syntax implements Cloneable {
        private String str;
        private final Pattern isNewline = Pattern.compile("\n");

        private Syntax(String str) {
            this.str = str;
        }

        public String getString() {
            return str;
        }


        public final void stripSurroundingSpaces() {
            str = str.trim();
        }

        public final void stripSpaceBefore() {
            String[] split = isSpace.split(str);
            int sl = split.length;
            int idx = 0;
            while(sl > idx && StringUtils.isNotEmpty(split[idx])) { idx++; }
            List<String> res = newArrayListWithCapacity(sl - idx + 2);
            for(int i = idx; sl > i; i++) { res.add(split[i]); }
            str = res.stream().collect(joining(" "));
        }

        public final void stripSpaceAfter() {
            String[] split = isSpace.split(str);
            int sl = split.length;
            int idx = sl - 1;
            while(idx > 0 && "".equals(split[idx])) { idx--; }
            List<String> res = newArrayListWithCapacity(idx + 2);
            for(int i = 0; idx > i; i++) { res.add(split[i]); }
            str = res.stream().collect(joining(" "));
        }

        public final void removeNewlines() {
            str = asList(isNewline.split(str)).stream().collect(joining(" "));
        }

        protected String getEscapedString() {
            return StringEscapeUtils.escapeJava(str);
        }

        @Override
        public Syntax clone() {
            return new Syntax(str);
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof Syntax) {
                return str.equals(((Syntax) o).str);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return str.hashCode();
        }
    }

    private class SyntaxString extends Syntax implements Cloneable {
        public SyntaxString(String str) {
            super(str);
        }

        @Override
        public SyntaxString clone() {
            return new SyntaxString(getString());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxString) {
                return super.equals((Syntax) o);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("String(%s)", getEscapedString());
        }
    }

    private class SyntaxKeyword extends Syntax implements Cloneable {
        public SyntaxKeyword(String str) {
            super(str);
        }

        @Override
        public SyntaxKeyword clone() {
            return new SyntaxKeyword(getString());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxKeyword) {
                return super.equals((Syntax) o);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("Keyword(%s)", getEscapedString());
        }
    }

    private class SyntaxValue extends Syntax implements Cloneable {
        public SyntaxValue(String str) {
            super(str);
        }

        @Override
        public SyntaxValue clone() {
            return new SyntaxValue(getString());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxValue) {
                return super.equals((Syntax) o);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("Value(%s)", getEscapedString());
        }
    }
}
