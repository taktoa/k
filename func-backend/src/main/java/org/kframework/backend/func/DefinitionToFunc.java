package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KSequence;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.OcamlIncludes.*;

/*
 * @author: Remy Goldschmidt
 */
public class DefinitionToFunc {
    public static final boolean annotateOutput = true;

    private final KExceptionManager kem;
    private final FileUtil files;
    private final GlobalOptions globalOptions;
    private final KompileOptions kompileOptions;

    private PreprocessedKORE preproc;

    public DefinitionToFunc(KExceptionManager kem,
                            FileUtil files,
                            GlobalOptions globalOptions,
                            KompileOptions kompileOptions) {
        this.kem = kem;
        this.files = files;
        this.globalOptions = globalOptions;
        this.kompileOptions = kompileOptions;
    }

    private FuncAST runtimeCodeToFunc(K k, int depth) {
        SyntaxBuilder sb = new SyntaxBuilder();
        System.out.println("Example:");
        System.out.println(new KOREtoKSTVisitor().apply(k).toString());
        sb.addImport("Def");
        sb.addImport("K");
        sb.addImport("Big_int");
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("_");
        sb.beginLetEquationValue();
        sb.append("print_string(print_k(try(run(");
        FuncVisitor convVisitor = oldConvert(preproc, sb, true, HashMultimap.create(), false);
        convVisitor.apply(preproc.runtimeProcess(k));
        sb.append(") (");
        sb.append(Integer.toString(depth));
        sb.append(")) with Stuck c' -> c'))");
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetExpression();
        return new FuncAST(sb.render());
    }

    private FuncAST langDefToFunc(PreprocessedKORE ppk) {
        return new FuncAST(mainConvert(ppk));
    }

    public String convert(CompiledDefinition def) {
        preproc = new PreprocessedKORE(def, kem, files, globalOptions, kompileOptions);
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println(preproc.getKSTModule().toString());
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        
        // System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        // System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        // String p = preproc.prettyPrint(); // DEBUG
        // System.out.println(p); // DEBUG
        // System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        // System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        return langDefToFunc(preproc).render();
    }

    public String convert(K k, int depth) {
        return runtimeCodeToFunc(k, depth).render();
    }

    private void addSortType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginTypeDefinition("sort");
        for (Sort s : ppk.definedSorts) {
            sb.beginConstructor();
            sb.append(encodeStringToIdentifier(s));
            sb.endConstructor();
        }
        if (!ppk.definedSorts.contains(Sorts.String())) {
            sb.addConstructor("SortString");
        }
        sb.endTypeDefinition();
    }

    private void addSortOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("order_sort(s: sort)");
        sb.beginLetEquationValue();
        sb.beginMatchExpression("s");

        int i = 0;

        for (Sort s : ppk.definedSorts) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(Integer.toString(i++));
        }

        sb.endMatchExpression();
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetEquation();
    }

    private void addKLabelType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginTypeDefinition("klabel");
        for (KLabel label : ppk.definedKLabels) {
            sb.beginConstructor();
            sb.append(encodeStringToIdentifier(label));
            sb.endConstructor();
        }
        sb.endTypeDefinition();
    }

    private void addKLabelOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("order_klabel(l: klabel)");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("l");

        int i = 0;

        for (KLabel label : ppk.definedKLabels) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(label));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(Integer.toString(i++));
            sb.endMatchEquation();
        }
        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintSortString(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_sort_string(c: sort) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (Sort s : ppk.definedSorts) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(StringUtil.enquoteKString(s.name())));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintSort(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_sort(c: sort) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (Sort s : ppk.definedSorts) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(s));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(s.name()));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addPrintKLabel(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("print_klabel(c: klabel) : string");
        sb.beginLetEquationValue();

        sb.beginMatchExpression("c");

        for (KLabel label : ppk.definedKLabels) {
            sb.beginMatchEquation();
            sb.beginMatchEquationPattern();
            sb.append(encodeStringToIdentifier(label));
            sb.endMatchEquationPattern();
            sb.addMatchEquationValue(StringUtil.enquoteCString(ToKast.apply(label)));
            sb.endMatchEquation();
        }

        sb.endMatchExpression();

        sb.endLetEquationValue();
        sb.endLetEquation();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private void addRules(PreprocessedKORE ppk, SyntaxBuilder sb) {
        int i = 0;
        for (List<KLabel> component : ppk.functionOrder) {
            boolean inLetrec = false;
            for (KLabel functionLabel : component) {
                if(inLetrec) {
                    sb.addLetrecEquationSeparator();
                } else {
                    sb.beginLetrecExpression();
                    sb.beginLetrecDefinitions();
                }
                sb.beginLetrecEquation();
                String functionName = encodeStringToFunction(functionLabel.name());
                sb.addLetrecEquationName(functionName + " (c: k list) (guards: Guard.t) : k");
                sb.beginLetrecEquationValue();
                sb.beginLetExpression();
                sb.beginLetDefinitions();
                sb.addLetEquation("lbl", encodeStringToIdentifier(functionLabel));
                sb.endLetDefinitions();
                sb.beginLetScope();
                sb.beginMatchExpression("c");
                String hook = ppk.attrLabels.get(Attribute.HOOK_KEY).getOrDefault(functionLabel, "");
                if (hooks.containsKey(hook)) {
                    sb.beginMatchEquation();
                    sb.append(hooks.get(hook));
                    sb.endMatchEquation();
                }
                if (predicateRules.containsKey(functionLabel.name())) {
                    sb.beginMatchEquation();
                    sb.append(predicateRules.get(functionLabel.name()));
                    sb.endMatchEquation();
                }

                i = 0;
                for (Rule r : ppk.functionRulesOrdered.getOrDefault(functionLabel, new ArrayList<>())) {
                    oldConvert(ppk, r, sb, true, i++, functionName);
                }
                sb.addMatchEquation("_", "raise (Stuck [KApply(lbl, c)])");
                sb.endMatchExpression();
                sb.endLetScope();
                sb.endLetExpression();
                sb.endLetrecEquationValue();
                sb.endLetrecEquation();
                inLetrec = true;
            }
            sb.endLetrecDefinitions();
            sb.endLetrecExpression();
        }

        sb.beginLetrecExpression();
        sb.beginLetrecDefinitions();
        sb.beginLetrecEquation();
        sb.addLetrecEquationName("lookups_step (c: k) (guards: Guard.t) : k");
        sb.beginLetrecEquationValue();
        sb.beginMatchExpression("c");
        i = 0;
        for (Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if (cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "lookups_step");
            }
        }
        sb.addMatchEquation("_", "raise (Stuck c)");
        sb.endLetrecEquationValue();
        sb.endLetrecEquation();
        sb.endLetrecDefinitions();
        sb.endLetrecExpression();
        sb.beginLetExpression();
        sb.beginLetDefinitions();
        sb.beginLetEquation();
        sb.addLetEquationName("step (c: k) : k");
        sb.beginLetEquationValue();
        sb.beginMatchExpression("c");
        for (Rule r : ppk.indexedRules.keySet()) {
            Set<String> cap = ppk.indexedRules.get(r);
            if (!cap.contains("lookup") && !cap.contains("function")) {
                oldConvert(ppk, r, sb, false, i++, "step");
            }
        }
        sb.addMatchEquation("_", "lookups_step c Guard.empty");
        sb.endLetEquationValue();
        sb.endLetDefinitions();
        sb.endLetExpression();
    }

    private String mainConvert(PreprocessedKORE ppk) {
        SyntaxBuilder sb = new SyntaxBuilder();

        addSortType(ppk, sb);
        addSortOrderFunc(ppk, sb);
        addKLabelType(ppk, sb);
        addKLabelOrderFunc(ppk, sb);
        addPrelude(sb);
        addPrintSortString(ppk, sb);
        addPrintKLabel(ppk, sb);
        addMidlude(sb);
        addRules(ppk, sb);
        addPostlude(sb);

        return sb.toString();
    }


    private void outputAnnotate(Rule r, SyntaxBuilder sb) {
        sb.beginMultilineComment();
        sb.append(" rule ");
        sb.append(ToKast.apply(r.body()));
        sb.append(" requires ");
        sb.append(ToKast.apply(r.requires()));
        sb.append(" ensures ");
        sb.append(ToKast.apply(r.ensures()));
        sb.append(" ");
        sb.append(r.att().toString());
        sb.endMultilineComment();
        sb.addNewline();
    }

    private void unhandledOldConvert(PreprocessedKORE ppk,
                                     Rule r,
                                     SyntaxBuilder sb,
                                     boolean function,
                                     int ruleNum,
                                     String functionName) throws KEMException {
        if(annotateOutput) { outputAnnotate(r, sb); }

        sb.append("| ");

        K left     = RewriteToTop.toLeft(r.body());
        K right    = RewriteToTop.toRight(r.body());
        K requires = r.requires();

        SetMultimap<KVariable, String> vars = HashMultimap.create();
        FuncVisitor visitor = oldConvert(ppk, sb, false, vars, false);

        if(function) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            visitor.apply(kapp.klist().items(), true);
        } else {
            visitor.apply(left);
        }

        String result = oldConvert(vars);

        if(ppk.indexedRules.get(r).contains("lookup")) {
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.append(Integer.toString(ruleNum));
            sb.append(") guards)");
        }

        String suffix = "";

        if(!(KSequence(BooleanUtils.TRUE).equals(requires)) || !("true".equals(result))) {
            suffix = oldConvertLookups(ppk, sb, requires, vars, functionName, ruleNum);
            sb.append(" when ");
            oldConvert(ppk, sb, true, vars, true).apply(requires);
            sb.append(" && (");
            sb.append(result);
            sb.append(")");
        }

        sb.append(" -> ");
        oldConvert(ppk, sb, true, vars, false).apply(right);
        sb.append(suffix);
        sb.addNewline();
    }

    private void oldConvert(PreprocessedKORE ppk,
                            Rule r,
                            SyntaxBuilder sb,
                            boolean function,
                            int ruleNum,
                            String functionName) {
        try {
            unhandledOldConvert(ppk, r, sb, function, ruleNum, functionName);
        } catch (KEMException e) {
            e.exception.addTraceFrame("while compiling rule at "
                                      + r.att().getOptional(Source.class).map(Object::toString).orElse("<none>")
                                      + ":"
                                      + r.att().getOptional(Location.class).map(Object::toString).orElse("<none>"));
            throw e;
        }
    }

    private static class Holder { int i; }

    private void checkApplyArity(KApply k, int arity, String funcName) throws KEMException {
        if(k.klist().size() != arity) {
            throw KEMException.internalError("Unexpected arity of " + funcName + ": " + k.klist().size(), k);
        }
    }

    private String oldConvertLookups(PreprocessedKORE ppk,
                                     SyntaxBuilder sb,
                                     K requires,
                                     SetMultimap<KVariable, String> vars,
                                     String functionName,
                                     int ruleNum) {
        Deque<String> suffix = new ArrayDeque<>();
        Holder h = new Holder();
        h.i = 0;
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                String str1, str2;
                int arity;
                String functionStr;

                List<K> kitems = k.klist().items();
                String klabel = k.klabel().name();

                switch(klabel) {
                case "#match":
                    str1 = "";
                    str2 = "";
                    functionStr = "lookup";
                    arity = 2;
                    break;
                case "#setChoice":
                    str1 = "| [Set s] -> let choice = (KSet.fold (fun e result -> if result = [Bottom] then (match e with ";
                    str2 = "| _ -> [Bottom]) else result) s [Bottom]) in if choice = [Bottom] then ("
                         + functionName
                         + " c (Guard.add (GuardElt.Guard "
                         + ruleNum
                         + ") guards)) else choice";
                    functionStr = "set choice";
                    arity = 2;
                    break;
                case "#mapChoice":
                    str1 = "| [Map m] -> let choice = (KMap.fold (fun k v result -> if result = [Bottom] then (match k with ";
                    str2 = "| _ -> [Bottom]) else result) m [Bottom]) in if choice = [Bottom] then ("
                         + functionName
                         + " c (Guard.add (GuardElt.Guard "
                         + ruleNum
                         + ") guards)) else choice";
                    functionStr = "map choice";
                    arity = 2;
                    break;
                default: return super.apply(k);
                }

                checkApplyArity(k, arity, functionStr);

                K fstKLabel = kitems.get(0);
                K sndKLabel = kitems.get(1);

                sb.append(" -> (match ");
                oldConvert(ppk, sb, true, vars, false).apply(sndKLabel);
                sb.append(" with ");
                sb.addNewline();
                sb.append(str1);
                oldConvert(ppk, sb, false, vars, false).apply(fstKLabel);
                suffix.add("| _ -> (" + functionName + " c (Guard.add (GuardElt.Guard " + ruleNum + ") guards)))");
                suffix.add(str2);
                h.i++;
                return super.apply(k);
            }
        }.apply(requires);

        SyntaxBuilder sb2 = new SyntaxBuilder();
        while(!suffix.isEmpty()) {
            sb2.append(suffix.pollLast());
        }
        return sb2.toString();
    }

    private static String oldConvert(SetMultimap<KVariable, String> vars) {
        SyntaxBuilder sb = new SyntaxBuilder();
        for (Collection<String> nonLinearVars : vars.asMap().values()) {
            if (nonLinearVars.size() < 2) {
                continue;
            }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                sb.append("(eq ");
                sb.append(last);
                sb.append(" ");
                sb.append(next);
                sb.append(")");
                last = next;
                sb.append(" && ");
            }
        }
        sb.append("true");
        return sb.toString();
    }

    private FuncVisitor oldConvert(PreprocessedKORE ppk,
                                   SyntaxBuilder sb,
                                   boolean rhs,
                                   SetMultimap<KVariable, String> vars,
                                   boolean useNativeBooleanExp) {
        return new FuncVisitor(ppk, sb, rhs, vars, useNativeBooleanExp);
    }
}
