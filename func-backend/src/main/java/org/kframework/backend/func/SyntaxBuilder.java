// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

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
    private static final boolean introduce = false;
    private final SyntaxTracker track = new SyntaxTracker();

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

    public XMLBuilder getXML() {
        return newxml().beginXML("body")
                       .append(this.pretty()
                                   .stream()
                                   .collect(joining()))
                       .endXML("body");
    }

    public int getNumParens() {
        return parens;
    }

    public int getNumLines() {
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

        if(s instanceof SyntaxEnum) {
            track.addSyntax((SyntaxEnum) s);
        }

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

    public Map<String, Integer> getTrack() {
        Map<String, Integer> res = newHashMap();
        EnumMap<SyntaxEnum, Integer> tm = track.getMap();
        for(SyntaxEnum se : tm.keySet()) {
            res.put(se.toString(), tm.get(se));
        }
        return res;
    }

    public String trackPrint() {
        return track.toString();
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

    public SyntaxBuilder addRender(SyntaxBuilder sb) {
        return beginRender().append(sb).endRender();
    }

    public SyntaxBuilder beginRender() {
        return append(SyntaxEnum.BEGIN_RENDER);
    }

    public SyntaxBuilder endRender() {
        return append(SyntaxEnum.END_RENDER);
    }

    public SyntaxBuilder addKeyword(Object keyword) {
        append(SyntaxEnum.BEGIN_KEYWORD);
        append(keyword.toString());
        append(SyntaxEnum.END_KEYWORD);
        return this;
    }

    public SyntaxBuilder addValue(Object value) {
        append(SyntaxEnum.BEGIN_VALUE);
        append(value.toString());
        append(SyntaxEnum.END_VALUE);
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

    public SyntaxBuilder addIntroduce(String... vars) {
        append(SyntaxEnum.BEGIN_INTRODUCE);
        for(String v : vars) {
            addName(v);
            addSpace();
        }
        append(SyntaxEnum.END_INTRODUCE);
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

    public SyntaxBuilder addComment(String c) {
        beginMultilineComment();
        append(c);
        endMultilineComment();
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

    public SyntaxBuilder beginTry() { //FIXME: this should do something
        return this;
    }

    public SyntaxBuilder addTryValue(SyntaxBuilder tryValueSB) { //FIXME: this should do something
        return this;
    }

    public SyntaxBuilder beginTryEquations() { //FIXME: this should do something
        return this;
    }

    public SyntaxBuilder addTryEquation(SyntaxBuilder a, SyntaxBuilder b) { //FIXME: this should do something
        return this;
    }

    public SyntaxBuilder endTryEquations() { //FIXME: this should do something
        return this;
    }

    public SyntaxBuilder endTry() { //FIXME: this should do something
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

        if(introduce) { addIntroduce(vars); }

        return this;
    }

    public SyntaxBuilder endLambda() {
        append(SyntaxEnum.END_LAMBDA_BODY);
        append(SyntaxEnum.END_LAMBDA);
        return this;
    }

    public SyntaxBuilder addEqualityTest(SyntaxBuilder a,
                                         SyntaxBuilder b) {
        beginRender();
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
        endRender();
        return this;
    }

    public SyntaxBuilder addConditional(SyntaxBuilder predicate,
                                        SyntaxBuilder trueVal,
                                        SyntaxBuilder falseVal) {
        beginConditional();
        addConditionalIf();
        append(predicate);
        addConditionalThen();
        append(trueVal);
        addConditionalElse();
        append(falseVal);
        endConditional();
        return this;
    }

    public SyntaxBuilder beginConditional() {
        return append(SyntaxEnum.BEGIN_CONDITIONAL);
    }

    public SyntaxBuilder endConditional() {
        return append(SyntaxEnum.END_CONDITIONAL);
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

    public SyntaxBuilder addSequence(SyntaxBuilder sb1, SyntaxBuilder sb2) { //FIXME stub
        return this;
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

    public SyntaxBuilder beginLetEquations() { //FIXME stub
        return this;
    }

    public SyntaxBuilder endLetEquations() { //FIXME stub
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

        return addMatchSB(value,
                          pats.stream().map(FuncUtil::newsbp).collect(Collectors.toList()),
                          vals.stream().map(FuncUtil::newsbv).collect(Collectors.toList()));
    }

    public SyntaxBuilder addMatchSB(SyntaxBuilder value,
                                  List<SyntaxBuilder> pats,
                                  List<SyntaxBuilder> vals) {
        beginMatchExpression(value);
        int size = Math.min(pats.size(), vals.size());
        for(int i = 0; size > i; i++) {
            addMatchEquation(pats.get(i), vals.get(i));
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
        return append(SyntaxEnum.BEGIN_MATCH_EQUATION_PAT).beginRender();
    }

    public SyntaxBuilder endMatchEquationPattern() {
        return endRender().append(SyntaxEnum.END_MATCH_EQUATION_PAT);
    }

    public SyntaxBuilder beginMatchEquationValue() {
        return append(SyntaxEnum.BEGIN_MATCH_EQUATION_VAL);
    }

    public SyntaxBuilder endMatchEquationValue() {
        return append(SyntaxEnum.END_MATCH_EQUATION_VAL);
    }

    public SyntaxBuilder beginTypeDefinition(String name, String... vars) {
        append(SyntaxEnum.BEGIN_TYPE_DEFINITION);
        append(SyntaxEnum.BEGIN_TYPE_DEFINITION_VARS);
        for(String v : vars) {
            append(SyntaxEnum.BEGIN_TYPE_DEFINITION_VARS);
            append(v);
            append(SyntaxEnum.END_TYPE_DEFINITION_VARS);
        }
        append(SyntaxEnum.END_TYPE_DEFINITION_VARS);
        append(SyntaxEnum.BEGIN_TYPE_DEFINITION_NAME);
        addName(name);
        append(SyntaxEnum.END_TYPE_DEFINITION_NAME);
        append(SyntaxEnum.BEGIN_TYPE_DEFINITION_CONS);
        return this;
    }

    public SyntaxBuilder endTypeDefinition() {
        append(SyntaxEnum.END_TYPE_DEFINITION_CONS);
        append(SyntaxEnum.END_TYPE_DEFINITION);
        return this;
    }

    public SyntaxBuilder addConstructor(String name, String... args) {
        beginConstructor();
        addConstructorName(name);
        addConstructorArgs(args);
        endConstructor();
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
        addName(con);
        endConstructorName();
        return this;
    }

    public SyntaxBuilder beginConstructor() {
        return append(SyntaxEnum.BEGIN_CONSTRUCTOR);
    }

    public SyntaxBuilder endConstructor() {
        return append(SyntaxEnum.END_CONSTRUCTOR);
    }

    public SyntaxBuilder beginConstructorName() {
        return append(SyntaxEnum.BEGIN_CONSTRUCTOR_NAME);
    }

    public SyntaxBuilder endConstructorName() {
        return append(SyntaxEnum.END_CONSTRUCTOR_NAME);
    }

    public SyntaxBuilder addConstructorArgs(String... args) {
        beginConstructorArgs();
        for(String arg : args) {
            addConstructorArg(arg);
        }
        endConstructorArgs();
        return this;
    }

    public SyntaxBuilder addConstructorArg(String arg) {
        beginConstructorArg();
        addName(arg);
        endConstructorArg();
        return this;
    }

    public SyntaxBuilder beginConstructorArg() {
        return append(SyntaxEnum.BEGIN_CONSTRUCTOR_ARGUMENT);
    }

    public SyntaxBuilder endConstructorArg() {
        return append(SyntaxEnum.END_CONSTRUCTOR_ARGUMENT);
    }

    public SyntaxBuilder beginConstructorArgs() {
        return append(SyntaxEnum.BEGIN_CONSTRUCTOR_ARGUMENTS);
    }

    public SyntaxBuilder endConstructorArgs() {
        return append(SyntaxEnum.END_CONSTRUCTOR_ARGUMENTS);
    }

    public SyntaxBuilder addType(SyntaxBuilder typename) {
        return append(typename);
    }

    public SyntaxBuilder addTypeProduct() {
        addSpace();
        addKeyword("*");
        addSpace();
        return this;
    }

    private class SyntaxTracker {
        private final EnumMap<SyntaxEnum, Integer> stxData;

        public SyntaxTracker() {
            stxData = new EnumMap<>(SyntaxEnum.class);
        }

        public EnumMap<SyntaxEnum, Integer> getMap() {
            return stxData;
        }

        public void addSyntax(SyntaxEnum s) {
            if(stxData.keySet().contains(s)) {
                stxData.put(s, stxData.get(s) + 1);
            } else {
                stxData.put(s, 1);
            }
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();
            for(SyntaxEnum se : stxData.keySet()) {
                out.append(String.format("\n%30s ---> %s\n", se, stxData.get(se)));
            }
            return out.toString();
        }
    }

    private enum XMLTagType {
        START,  // tag start,     e.g.: <example>
        STOP,   // tag stop,      e.g.: </example>
        SING;   // tag singleton, e.g.: <example />
    }


    private enum SyntaxEnum implements Syntax {
        SPACE                       (xSing(),  "space",                 " "),
        NEWLINE                     (xSing(),  "newline",               "\n"),

        BEGIN_RENDER                (xStart(), "rend",                  ""),
        END_RENDER                  (xStop(),  "rend",                  ""),

        BEGIN_KEYWORD               (xStart(), "keyword",               ""),
        END_KEYWORD                 (xStop(),  "keyword",               ""),

        BEGIN_VALUE                 (xStart(), "value",                 ""),
        END_VALUE                   (xStop(),  "value",                 ""),

        BEGIN_PARENTHESIS           (xStart(), "paren",                 "("),
        END_PARENTHESIS             (xStop(),  "paren",                 ")"),

        BEGIN_COMMENT               (xStart(), "comment",               "(*"),
        END_COMMENT                 (xStop(),  "comment",               "*)"),

        BEGIN_NAME                  (xStart(), "name",                  ""),
        END_NAME                    (xStop(),  "name",                  ""),

        BEGIN_INTRODUCE             (xStart(), "introduce",             " (* introduce "),
        END_INTRODUCE               (xStop(),  "introduce",             " *) "),

        BEGIN_INTEGER               (xStart(), "integer",               ""),
        END_INTEGER                 (xStop(),  "integer",               ""),
        BEGIN_FLOAT                 (xStart(), "float",                 ""),
        END_FLOAT                   (xStop(),  "float",                 ""),
        BEGIN_BOOLEAN               (xStart(), "boolean",               ""),
        END_BOOLEAN                 (xStop(),  "boolean",               ""),
        BEGIN_STRING                (xStart(), "string",                ""),
        END_STRING                  (xStop(),  "string",                ""),
        BEGIN_TYPE                  (xStart(), "type",                  ""),
        END_TYPE                    (xStop(),  "type",                  ""),

        BEGIN_REFERENCE_VARIABLE    (xStart(), "ref",                   ""),
        END_REFERENCE_VARIABLE      (xStop(),  "ref",                   ""),

        BEGIN_CONDITIONAL           (xStart(), "conditional",           "("),
        END_CONDITIONAL             (xStop(),  "conditional",           ")"),
        CONDITIONAL_IF              (xSing(),  "conditional-if",        "if "),
        CONDITIONAL_THEN            (xSing(),  "conditional-then",      " then "),
        CONDITIONAL_ELSE            (xSing(),  "conditional-else",      " else "),

        BEGIN_MATCH_EXPRESSION      (xStart(), "match-expression",      "("),
        END_MATCH_EXPRESSION        (xStop(),  "match-expression",      ")"),
        BEGIN_MATCH_INPUT           (xStart(), "match-input",           "match "),
        END_MATCH_INPUT             (xStop(),  "match-input",           " with "),
        BEGIN_MATCH_EQUATIONS       (xStart(), "match-equations",       ""),
        END_MATCH_EQUATIONS         (xStop(),  "match-equations",       ""),
        BEGIN_MATCH_EQUATION        (xStart(), "match-equation",        "\n| "),
        END_MATCH_EQUATION          (xStop(),  "match-equation",        ""),
        BEGIN_MATCH_EQUATION_VAL    (xStart(), "match-equation-val",    "("),
        END_MATCH_EQUATION_VAL      (xStop(),  "match-equation-val",    ")"),
        BEGIN_MATCH_EQUATION_PAT    (xStart(), "match-equation-pat",    ""),
        END_MATCH_EQUATION_PAT      (xStop(),  "match-equation-pat",    " -> "),

        BEGIN_LET_EXPRESSION        (xStart(), "let-expression",        "(let "),
        END_LET_EXPRESSION          (xStop(),  "let-expression",        ")"),
        BEGIN_LET_DECLARATION       (xStart(), "let-declaration",       "let "),
        END_LET_DECLARATION         (xStop(),  "let-declaration",       "\n"),
        BEGIN_LET_DEFINITIONS       (xStart(), "let-definitions",       ""),
        END_LET_DEFINITIONS         (xStop(),  "let-definitions",       "_ = 1"),
        BEGIN_LET_EQUATION          (xStart(), "let-equation",          ""),
        END_LET_EQUATION            (xStop(),  "let-equation",          " and "),
        BEGIN_LET_EQUATION_NAME     (xStart(), "let-equation-name",     ""),
        END_LET_EQUATION_NAME       (xStop(),  "let-equation-name",     " = "),
        BEGIN_LET_EQUATION_VAL      (xStart(), "let-equation-val",      "("),
        END_LET_EQUATION_VAL        (xStop(),  "let-equation-val",      ")"),
        BEGIN_LET_SCOPE             (xStart(), "let-scope",             " in ("),
        END_LET_SCOPE               (xStop(),  "let-scope",             ")"),

        BEGIN_LETREC_EXPRESSION     (xStart(), "letrec-expression",     "(let rec "),
        END_LETREC_EXPRESSION       (xStop(),  "letrec-expression",     ")"),
        BEGIN_LETREC_DECLARATION    (xStart(), "letrec-declaration",    "let rec "),
        END_LETREC_DECLARATION      (xStop(),  "letrec-declaration",    "\n"),
        BEGIN_LETREC_DEFINITIONS    (xStart(), "letrec-definitions",    ""),
        END_LETREC_DEFINITIONS      (xStop(),  "letrec-definitions",    "throwaway = 1"),
        BEGIN_LETREC_EQUATION       (xStart(), "letrec-equation",       ""),
        END_LETREC_EQUATION         (xStop(),  "letrec-equation",       " and "),
        BEGIN_LETREC_EQUATION_NAME  (xStart(), "letrec-equation-name",  ""),
        END_LETREC_EQUATION_NAME    (xStop(),  "letrec-equation-name",  " = "),
        BEGIN_LETREC_EQUATION_VAL   (xStart(), "letrec-equation-val",   "("),
        END_LETREC_EQUATION_VAL     (xStop(),  "letrec-equation-val",   ")"),
        BEGIN_LETREC_SCOPE          (xStart(), "letrec-scope",          " in ("),
        END_LETREC_SCOPE            (xStop(),  "letrec-scope",          ")"),

        BEGIN_LAMBDA                (xStart(), "lam",                   "(fun"),
        END_LAMBDA                  (xStop(),  "lam",                   ")"),
        BEGIN_LAMBDA_VAR            (xStart(), "lam-var",               " "),
        END_LAMBDA_VAR              (xStop(),  "lam-var",               ""),
        BEGIN_LAMBDA_VARS           (xStart(), "lam-vars",              ""),
        END_LAMBDA_VARS             (xStop(),  "lam-vars",              " -> "),
        BEGIN_LAMBDA_BODY           (xStart(), "lam-body",              "("),
        END_LAMBDA_BODY             (xStop(),  "lam-body",              ")"),

        BEGIN_APPLICATION           (xStart(), "application",           "("),
        END_APPLICATION             (xStop(),  "application",           ")"),
        BEGIN_FUNCTION              (xStart(), "function",              ""),
        END_FUNCTION                (xStop(),  "function",              ""),
        BEGIN_ARGUMENT              (xStart(), "argument",              " ("),
        END_ARGUMENT                (xStop(),  "argument",              ")"),

        BEGIN_TYPE_DEFINITION       (xStart(), "type-definition",       "type"),
        END_TYPE_DEFINITION         (xStop(),  "type-definition",       ""),
        BEGIN_TYPE_DEFINITION_VAR   (xStart(), "type-definition-var",   " "),
        END_TYPE_DEFINITION_VAR     (xStop(),  "type-definition-var",   ""),
        BEGIN_TYPE_DEFINITION_VARS  (xStart(), "type-definition-vars",  ""),
        END_TYPE_DEFINITION_VARS    (xStop(),  "type-definition-vars",  " "),
        BEGIN_TYPE_DEFINITION_NAME  (xStart(), "type-definition-name",  ""),
        END_TYPE_DEFINITION_NAME    (xStop(),  "type-definition-name",  " = \n"),
        BEGIN_TYPE_DEFINITION_CONS  (xStart(), "type-definition-cons",  ""),
        END_TYPE_DEFINITION_CONS    (xStop(),  "type-definition-cons",  ""),

        BEGIN_CONSTRUCTOR           (xStart(), "constructor",           "| "),
        END_CONSTRUCTOR             (xStop(),  "constructor",           "\n"),
        BEGIN_CONSTRUCTOR_NAME      (xStart(), "constructor-name",      ""),
        END_CONSTRUCTOR_NAME        (xStop(),  "constructor-name",      ""),
        BEGIN_CONSTRUCTOR_ARGUMENT  (xStart(), "constructor-argument",  ""),
        END_CONSTRUCTOR_ARGUMENT    (xStop(),  "constructor-argument",  ""),
        BEGIN_CONSTRUCTOR_ARGUMENTS (xStart(), "constructor-arguments", " of ("),
        END_CONSTRUCTOR_ARGUMENTS   (xStop(),  "constructor-arguments", ")");

        private final XMLTagType type;
        private final String name;
        private String str;

        private SyntaxEnum(XMLTagType type, String name, String str) {
            this.type = type;
            this.name = name;
            this.str  = str;
        }

        // Just for abbreviating the enum definitions
        private static XMLTagType xStart() { return XMLTagType.START; }
        private static XMLTagType xStop()  { return XMLTagType.STOP; }
        private static XMLTagType xSing()  { return XMLTagType.SING; }

        public XMLBuilder appendXML(XMLBuilder xml) {
            switch(type) {
            case START:
                xml.beginXML(name);
                break;
            case STOP:
                xml.endXML(name);
                break;
            case SING:
                xml.addXML(name);
                break;
            default:
                assert false;
                break;
            }
            return xml;
        }

        @Override
        public String render() { return str; }

        @Override
        public void setRenderString(String str) { this.str = str; }

        @Override
        public String toString() {
            return appendXML(new XMLBuilder()).toString();
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
            return StringEscapeUtils.escapeXml(render());
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
            return String.format("%s", getEscapedString());
        }
    }
}
