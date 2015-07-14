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
    private static final boolean introduce = true;

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
        if("(".equals(s.render())) {
            parens++;
        } else if(s.render().contains("(")) {
            Matcher m = isOpenParen.matcher(s.render());
            while(m.find()) { parens++; }
        }

        if(")".equals(s.render())) {
            parens--;
        } else if(s.render().contains(")")) {
            Matcher m = isCloseParen.matcher(s.render());
            while(m.find()) { parens--; }
        }

        if("\n".equals(s.render())) {
            linum++;
        } else if(s.render().contains("\n")) {
            Matcher m = isNewline.matcher(s.render());
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
            append(s);
        }
        return this;
    }

    public SyntaxBuilder appendf(String format, Object... args) {
        return append(String.format(format, args));
    }




    public String render() {
        StringBuilder sb = new StringBuilder();
        for(Syntax s : stx) {
            sb.append(s.render());
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
            res.append(s);
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

    public SyntaxBuilder addName(String name) {
        append(SyntaxEnum.BEGIN_NAME);
        append(name);
        append(SyntaxEnum.END_NAME);
        return this;
    }

    public SyntaxBuilder addSpace() {
        return append(SyntaxEnum.SPACE);
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
        return append(SyntaxEnum.NEWLINE);
    }

    public SyntaxBuilder stripSurroundingSpaces() {
        return stripSpaceBefore().stripSpaceAfter();
    }

    public SyntaxBuilder stripSpaceBefore() {
        synchronized(stx) {
            while(isSpace.matcher(stx.get(0).render()).matches()) {
                stx.remove(0);
            }
            stx.get(0).stripSpaceBefore();
        }
        return this;
    }

    public SyntaxBuilder stripSpaceAfter() {
        synchronized(stx) {
            int size = stx.size();
            while(isSpace.matcher(stx.get(size - 1).render()).matches()) {
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
        return append(SyntaxEnum.BEGIN_COMMENT);
    }

    public SyntaxBuilder endMultilineComment() {
        return append(SyntaxEnum.END_COMMENT);
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
        return append(SyntaxEnum.BEGIN_APPLICATION);
    }

    public SyntaxBuilder endApplication() {
        return append(SyntaxEnum.END_APPLICATION);
    }

    public SyntaxBuilder addFunction(String fnName) {
        append(SyntaxEnum.BEGIN_FUNCTION);
        addName(fnName);
        append(SyntaxEnum.END_FUNCTION);
        return this;
    }

    public SyntaxBuilder addArgument(SyntaxBuilder arg) {
        return beginArgument().append(arg).endArgument();
    }

    public SyntaxBuilder beginArgument() {
        return append(SyntaxEnum.BEGIN_ARGUMENT);
    }

    public SyntaxBuilder endArgument() {
        return append(SyntaxEnum.END_ARGUMENT);
    }

    public SyntaxBuilder beginLambda(String... vars) {
        append(SyntaxEnum.BEGIN_LAMBDA);
        append(SyntaxEnum.BEGIN_LAMBDA_VARS);

        for(String v : vars) {
            append(SyntaxEnum.BEGIN_LAMBDA_VAR);
            addName(v);
            append(SyntaxEnum.END_LAMBDA_VAR);
        }

        append(SyntaxEnum.END_LAMBDA_VARS);
        append(SyntaxEnum.BEGIN_LAMBDA_BODY);

        if(introduce) {
            append(SyntaxEnum.BEGIN_INTRODUCE);
            for(String v : vars) {
                addName(v);
                addSpace();
            }
            append(SyntaxEnum.END_INTRODUCE);
        }

        return this;
    }

    public SyntaxBuilder endLambda() {
        append(SyntaxEnum.END_LAMBDA_BODY);
        append(SyntaxEnum.END_LAMBDA);
        return this;
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
        return append(SyntaxEnum.CONDITIONAL_IF);
    }

    public SyntaxBuilder addConditionalThen() {
        return append(SyntaxEnum.CONDITIONAL_THEN);
    }

    public SyntaxBuilder addConditionalElse() {
        return append(SyntaxEnum.CONDITIONAL_ELSE);
    }

    public SyntaxBuilder beginParenthesis() {
        return append(SyntaxEnum.BEGIN_PARENTHESIS);
    }

    public SyntaxBuilder endParenthesis() {
        return append(SyntaxEnum.END_PARENTHESIS);
    }



    public SyntaxBuilder addGlobalLet(SyntaxBuilder name,
                                      SyntaxBuilder value) {
        beginLetDeclaration();
        beginLetDefinitions();
        addLetEquation(name, value);
        endLetDefinitions();
        endLetDeclaration();
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
        return append(SyntaxEnum.BEGIN_LET_EQUATION);
    }

    public SyntaxBuilder endLetEquation() {
        return append(SyntaxEnum.END_LET_EQUATION);
    }

    public SyntaxBuilder beginLetEquationName() {
        return append(SyntaxEnum.BEGIN_LET_EQUATION_NAME);
    }

    public SyntaxBuilder endLetEquationName() {
        return append(SyntaxEnum.END_LET_EQUATION_NAME);
    }

    public SyntaxBuilder beginLetEquationValue() {
        return append(SyntaxEnum.BEGIN_LET_EQUATION_VAL);
    }

    public SyntaxBuilder endLetEquationValue() {
        return append(SyntaxEnum.END_LET_EQUATION_VAL);
    }

    public SyntaxBuilder addLetEquationSeparator() {
        // addNewline();
        // addSpace();
        // addKeyword("and");
        // addSpace();
        // return this;
        return this; // likely bug spot
    }

    public SyntaxBuilder beginLetDefinitions() {
        return append(SyntaxEnum.BEGIN_LET_DEFINITIONS);
    }

    public SyntaxBuilder endLetDefinitions() {
        return append(SyntaxEnum.END_LET_DEFINITIONS);
    }

    public SyntaxBuilder beginLetScope() {
        return append(SyntaxEnum.BEGIN_LET_SCOPE);
    }

    public SyntaxBuilder endLetScope() {
        return append(SyntaxEnum.END_LET_SCOPE);
    }

    public SyntaxBuilder beginLetExpression() {
        return append(SyntaxEnum.BEGIN_LET_EXPRESSION);
    }

    public SyntaxBuilder endLetExpression() {
        return append(SyntaxEnum.END_LET_EXPRESSION);
    }

    public SyntaxBuilder beginLetDeclaration() {
        return append(SyntaxEnum.BEGIN_LET_DECLARATION);
    }

    public SyntaxBuilder endLetDeclaration() {
        return append(SyntaxEnum.END_LET_DECLARATION);
    }




    public SyntaxBuilder addGlobalLetrec(SyntaxBuilder name,
                                         SyntaxBuilder value) {
        beginLetrecDeclaration();
        beginLetrecDefinitions();
        addLetrecEquation(name, value);
        endLetrecDefinitions();
        endLetrecDeclaration();
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
        return append(SyntaxEnum.BEGIN_LETREC_EQUATION);
    }

    public SyntaxBuilder endLetrecEquation() {
        return append(SyntaxEnum.END_LETREC_EQUATION);
    }

    public SyntaxBuilder beginLetrecEquationName() {
        return append(SyntaxEnum.BEGIN_LETREC_EQUATION_NAME);
    }

    public SyntaxBuilder endLetrecEquationName() {
        return append(SyntaxEnum.END_LETREC_EQUATION_NAME);
    }

    public SyntaxBuilder beginLetrecEquationValue() {
        return append(SyntaxEnum.BEGIN_LETREC_EQUATION_VAL);
    }

    public SyntaxBuilder endLetrecEquationValue() {
        return append(SyntaxEnum.END_LETREC_EQUATION_VAL);
    }

    public SyntaxBuilder addLetrecEquationSeparator() {
        // addNewline();
        // addSpace();
        // addKeyword("and");
        // addSpace();
        // return this;
        return this; // likely bug spot
    }

    public SyntaxBuilder beginLetrecDefinitions() {
        return append(SyntaxEnum.BEGIN_LETREC_DEFINITIONS);
    }

    public SyntaxBuilder endLetrecDefinitions() {
        return append(SyntaxEnum.END_LETREC_DEFINITIONS);
    }

    public SyntaxBuilder beginLetrecScope() {
        return append(SyntaxEnum.BEGIN_LETREC_SCOPE);
    }

    public SyntaxBuilder endLetrecScope() {
        return append(SyntaxEnum.END_LETREC_SCOPE);
    }

    public SyntaxBuilder beginLetrecExpression() {
        return append(SyntaxEnum.BEGIN_LETREC_EXPRESSION);
    }

    public SyntaxBuilder endLetrecExpression() {
        return append(SyntaxEnum.END_LETREC_EXPRESSION);
    }

    public SyntaxBuilder beginLetrecDeclaration() {
        return append(SyntaxEnum.BEGIN_LETREC_DECLARATION);
    }

    public SyntaxBuilder endLetrecDeclaration() {
        return append(SyntaxEnum.END_LETREC_DECLARATION);
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
        append(SyntaxEnum.BEGIN_MATCH_EXPRESSION);
        append(SyntaxEnum.BEGIN_MATCH_INPUT);
        append(varname);
        append(SyntaxEnum.END_MATCH_INPUT);
        append(SyntaxEnum.BEGIN_MATCH_EQUATIONS);
        return this;
    }

    public SyntaxBuilder endMatchExpression() {
        append(SyntaxEnum.END_MATCH_EQUATIONS);
        append(SyntaxEnum.END_MATCH_EXPRESSION);
        return this;
    }

    public SyntaxBuilder beginMatchEquation() {
        return append(SyntaxEnum.BEGIN_MATCH_EQUATION);
    }

    public SyntaxBuilder endMatchEquation() {
        return append(SyntaxEnum.END_MATCH_EQUATION);
    }

    public SyntaxBuilder beginMatchEquationPattern() {
        return append(SyntaxEnum.BEGIN_MATCH_EQUATION_PAT);
    }

    public SyntaxBuilder endMatchEquationPattern() {
        return append(SyntaxEnum.END_MATCH_EQUATION_PAT);
    }

    public SyntaxBuilder beginMatchEquationValue() {
        return append(SyntaxEnum.BEGIN_MATCH_EQUATION_VAL);
    }

    public SyntaxBuilder endMatchEquationValue() {
        return append(SyntaxEnum.END_MATCH_EQUATION_VAL);
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








    private enum SyntaxEnum implements Syntax {
        SPACE                       ("SPACE",                       " "),
        NEWLINE                     ("NEWLINE",                     "\n"),

        BEGIN_PARENTHESIS           ("BEGIN_PARENTHESIS",           "("),
        END_PARENTHESIS             ("END_PARENTHESIS",             ")"),

        BEGIN_COMMENT               ("BEGIN_COMMENT",               "(*"),
        END_COMMENT                 ("END_COMMENT",                 "*)"),

        BEGIN_NAME                  ("BEGIN_NAME",                  ""),
        END_NAME                    ("END_NAME",                    ""),

        BEGIN_INTRODUCE             ("BEGIN_INTRODUCE",             " (* introduce "),
        END_INTRODUCE               ("END_INTRODUCE",               "*) "),

        BEGIN_INTEGER               ("BEGIN_INTEGER",               ""),
        END_INTEGER                 ("END_INTEGER",                 ""),
        BEGIN_FLOAT                 ("BEGIN_FLOAT",                 ""),
        END_FLOAT                   ("END_FLOAT",                   ""),
        BEGIN_BOOLEAN               ("BEGIN_BOOLEAN",               ""),
        END_BOOLEAN                 ("END_BOOLEAN",                 ""),
        BEGIN_STRING                ("BEGIN_STRING",                ""),
        END_STRING                  ("END_STRING",                  ""),
        BEGIN_TYPE                  ("BEGIN_TYPE",                  ""),
        END_TYPE                    ("END_TYPE",                    ""),

        CONDITIONAL_BEGIN           ("CONDITIONAL_BEGIN",           "("),
        CONDITIONAL_END             ("CONDITIONAL_END",             ")"),
        CONDITIONAL_IF              ("CONDITIONAL_IF",              "if "),
        CONDITIONAL_THEN            ("CONDITIONAL_THEN",            " then "),
        CONDITIONAL_ELSE            ("CONDITIONAL_ELSE",            " else "),

        BEGIN_MATCH_EXPRESSION      ("BEGIN_MATCH_EXPRESSION",      "("),
        END_MATCH_EXPRESSION        ("END_MATCH_EXPRESSION",        ")"),
        BEGIN_MATCH_INPUT           ("BEGIN_MATCH_INPUT",           "match "),
        END_MATCH_INPUT             ("END_MATCH_INPUT",             " with "),
        BEGIN_MATCH_EQUATIONS       ("BEGIN_MATCH_EQUATIONS",       ""),
        END_MATCH_EQUATIONS         ("END_MATCH_EQUATIONS",         ""),
        BEGIN_MATCH_EQUATION        ("BEGIN_MATCH_EQUATION",        "\n| "),
        END_MATCH_EQUATION          ("END_MATCH_EQUATION",          ""),
        BEGIN_MATCH_EQUATION_VAL    ("BEGIN_MATCH_EQUATION_VAL",    "("),
        END_MATCH_EQUATION_VAL      ("END_MATCH_EQUATION_VAL",      ")"),
        BEGIN_MATCH_EQUATION_PAT    ("BEGIN_MATCH_EQUATION_PAT",    ""),
        END_MATCH_EQUATION_PAT      ("END_MATCH_EQUATION_PAT",      " -> "),

        BEGIN_LET_EXPRESSION        ("BEGIN_LET_EXPRESSION",        "(let "),
        END_LET_EXPRESSION          ("END_LET_EXPRESSION",          ")\n"),
        BEGIN_LET_DECLARATION       ("BEGIN_LET_DECLARATION",       "let "),
        END_LET_DECLARATION         ("END_LET_DECLARATION",         "\n"),
        BEGIN_LET_DEFINITIONS       ("BEGIN_LET_DEFINITIONS",       ""),
        END_LET_DEFINITIONS         ("END_LET_DEFINITIONS",         "_ = 1"),
        BEGIN_LET_EQUATION          ("BEGIN_LET_EQUATION",          ""),
        END_LET_EQUATION            ("END_LET_EQUATION",            " and "),
        BEGIN_LET_EQUATION_NAME     ("BEGIN_LET_EQUATION_NAME",     ""),
        END_LET_EQUATION_NAME       ("END_LET_EQUATION_NAME",       " = "),
        BEGIN_LET_EQUATION_VAL      ("BEGIN_LET_EQUATION_VAL",      "("),
        END_LET_EQUATION_VAL        ("END_LET_EQUATION_VAL",        ")"),
        BEGIN_LET_SCOPE             ("BEGIN_LET_SCOPE",             " in ("),
        END_LET_SCOPE               ("END_LET_SCOPE",               ")"),

        BEGIN_LETREC_EXPRESSION     ("BEGIN_LETREC_EXPRESSION",     "(let rec "),
        END_LETREC_EXPRESSION       ("END_LETREC_EXPRESSION",       ")\n"),
        BEGIN_LETREC_DECLARATION    ("BEGIN_LETREC_DECLARATION",    "let rec "),
        END_LETREC_DECLARATION      ("END_LETREC_DECLARATION",      "\n"),
        BEGIN_LETREC_DEFINITIONS    ("BEGIN_LETREC_DEFINITIONS",    ""),
        END_LETREC_DEFINITIONS      ("END_LETREC_DEFINITIONS",      "throwaway = 1"),
        BEGIN_LETREC_EQUATION       ("BEGIN_LETREC_EQUATION",       ""),
        END_LETREC_EQUATION         ("END_LETREC_EQUATION",         " and "),
        BEGIN_LETREC_EQUATION_NAME  ("BEGIN_LETREC_EQUATION_NAME",  ""),
        END_LETREC_EQUATION_NAME    ("END_LETREC_EQUATION_NAME",    " = "),
        BEGIN_LETREC_EQUATION_VAL   ("BEGIN_LETREC_EQUATION_VAL",   "("),
        END_LETREC_EQUATION_VAL     ("END_LETREC_EQUATION_VAL",     ")"),
        BEGIN_LETREC_SCOPE          ("BEGIN_LETREC_SCOPE",          " in ("),
        END_LETREC_SCOPE            ("END_LETREC_SCOPE",            ")"),

        BEGIN_LAMBDA                ("BEGIN_LAMBDA",                "(fun"),
        END_LAMBDA                  ("END_LAMBDA",                  ")"),
        BEGIN_LAMBDA_VAR            ("BEGIN_LAMBDA_VAR",            " "),
        END_LAMBDA_VAR              ("END_LAMBDA_VAR",              ""),
        BEGIN_LAMBDA_VARS           ("BEGIN_LAMBDA_VARS",           ""),
        END_LAMBDA_VARS             ("END_LAMBDA_VARS",             " -> "),
        BEGIN_LAMBDA_BODY           ("BEGIN_LAMBDA_BODY",           "("),
        END_LAMBDA_BODY             ("END_LAMBDA_BODY",             ")"),

        BEGIN_APPLICATION           ("BEGIN_APPLICATION",           "("),
        END_APPLICATION             ("END_APPLICATION",             ")"),
        BEGIN_FUNCTION              ("BEGIN_FUNCTION",              ""),
        END_FUNCTION                ("END_FUNCTION",                ""),
        BEGIN_ARGUMENT              ("BEGIN_ARGUMENT",              "("),
        END_ARGUMENT                ("END_ARGUMENT",                ")"),

        BEGIN_TYPE_DEFINITION       ("BEGIN_TYPE_DEFINITION",       "type "),
        END_TYPE_DEFINITION         ("END_TYPE_DEFINITION",         ""),
        BEGIN_TYPE_DEFINITION_VAR   ("BEGIN_TYPE_DEFINITION_VAR",   ""),
        END_TYPE_DEFINITION_VAR     ("END_TYPE_DEFINITION_VAR",     ""),
        BEGIN_TYPE_DEFINITION_VARS  ("BEGIN_TYPE_DEFINITION_VARS",  ""),
        END_TYPE_DEFINITION_VARS    ("END_TYPE_DEFINITION_VARS",    ""),
        BEGIN_TYPE_DEFINITION_NAME  ("BEGIN_TYPE_DEFINITION_NAME",  ""),
        END_TYPE_DEFINITION_NAME    ("END_TYPE_DEFINITION_NAME",    " = "),
        BEGIN_TYPE_DEFINITION_CONS  ("BEGIN_TYPE_DEFINITION_CONS",  ""),
        END_TYPE_DEFINITION_CONS    ("END_TYPE_DEFINITION_CONS",    ""),

        BEGIN_CONSTRUCTOR           ("BEGIN_CONSTRUCTOR",           ""),
        END_CONSTRUCTOR             ("END_CONSTRUCTOR",             ""),
        BEGIN_CONSTRUCTOR_NAME      ("BEGIN_CONSTRUCTOR_NAME",      ""),
        END_CONSTRUCTOR_NAME        ("END_CONSTRUCTOR_NAME",        ""),
        BEGIN_CONSTRUCTOR_ARGUMENT  ("BEGIN_CONSTRUCTOR_ARGUMENT",  ""),
        END_CONSTRUCTOR_ARGUMENT    ("END_CONSTRUCTOR_ARGUMENT",    ""),
        BEGIN_CONSTRUCTOR_ARGUMENTS ("BEGIN_CONSTRUCTOR_ARGUMENTS", " of ("),
        END_CONSTRUCTOR_ARGUMENTS   ("END_CONSTRUCTOR_ARGUMENTS",   ")");

        private final String name;
        private String str;

        private SyntaxEnum(String name, String str) {
            this.name = name;
            this.str  = str;
        }

        @Override
        public String render() { return str; }

        @Override
        public void setRenderString(String str) { this.str = str; }

        @Override
        public String toString() {
            return name;
        }
    }


    private interface Syntax {
        Pattern isNewline = Pattern.compile("\n");
        String render();
        void setRenderString(String s);

        public default void stripSurroundingSpaces() {
            setRenderString(render().trim());
        }

        public default void stripSpaceBefore() {
            String str = render();
            String[] split = isSpace.split(str);
            int sl = split.length;
            int idx = 0;
            while(sl > idx && StringUtils.isNotEmpty(split[idx])) { idx++; }
            List<String> res = newArrayListWithCapacity(sl - idx + 2);
            for(int i = idx; sl > i; i++) { res.add(split[i]); }
            setRenderString(res.stream().collect(joining(" ")));
        }

        public default void stripSpaceAfter() {
            String str = render();
            String[] split = isSpace.split(str);
            int sl = split.length;
            int idx = sl - 1;
            while(idx > 0 && "".equals(split[idx])) { idx--; }
            List<String> res = newArrayListWithCapacity(idx + 2);
            for(int i = 0; idx > i; i++) { res.add(split[i]); }
            setRenderString(res.stream().collect(joining(" ")));
        }

        public default void removeNewlines() {
            setRenderString(asList(isNewline.split(render()))
                            .stream()
                            .collect(joining(" ")));
        }

        public default String getEscapedString() {
            return StringEscapeUtils.escapeJava(render());
        }
    }

    private class SyntaxString implements Syntax, Cloneable {
        private String str;

        public SyntaxString(String str) {
            setRenderString(str);
        }

        @Override
        public String render() { return str; }

        @Override
        public void setRenderString(String str) { this.str = str; }

        @Override
        public SyntaxString clone() {
            return new SyntaxString(render());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxString) {
                return render().equals(((SyntaxString) o).render());
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("String(%s)", getEscapedString());
        }
    }

    private class SyntaxKeyword implements Syntax, Cloneable {
        private String str;

        public SyntaxKeyword(String str) {
            setRenderString(str);
        }

        @Override
        public String render() { return str; }

        @Override
        public void setRenderString(String str) { this.str = str; }

        @Override
        public SyntaxKeyword clone() {
            return new SyntaxKeyword(render());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxKeyword) {
                return render().equals(((SyntaxKeyword) o).render());
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("Keyword(%s)", getEscapedString());
        }
    }

    private class SyntaxValue implements Syntax, Cloneable {
        private String str;

        public SyntaxValue(String str) {
            setRenderString(str);
        }

        @Override
        public String render() { return str; }

        @Override
        public void setRenderString(String str) { this.str = str; }

        @Override
        public SyntaxValue clone() {
            return new SyntaxValue(render());
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof SyntaxValue) {
                return render().equals(((SyntaxValue) o).render());
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("Value(%s)", getEscapedString());
        }
    }
}
