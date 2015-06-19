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
import java.util.function.Function;
import java.util.stream.Stream;
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
        FuncVisitor convVisitor = oldConvert(preproc, true, HashMultimap.create(), false);
        sb.append(convVisitor.apply(preproc.runtimeProcess(k)));
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
        System.out.println(preproc.prettyPrint()); // DEBUG
        // System.out.println(SortCheck.sortCheck(preproc.getKSTModule())); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        System.out.println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG"); // DEBUG
        return langDefToFunc(preproc).render();
    }

    public String convert(K k, int depth) {
        return runtimeCodeToFunc(k, depth).render();
    }

    private List<Integer> rangeInclusive(int min, int step, int max) {
        int elements = Math.abs((max - min) / step);
        int padding = 4;
        List<Integer> result = new ArrayList<>(elements + padding);
        for(int i = min; i <= max; i += step) {
            result.add(new Integer(i));
        }
        return result;
    }

    private List<Integer> rangeExclusive(int min, int step, int max) {
        List<Integer> result = rangeInclusive(min, step, max);
        result.remove(0);
        result.remove(result.size() - 1);
        return result;
    }

    private List<Integer> rangeInclusive(int min, int max) {
        return rangeInclusive(min, 1, max);
    }

    private List<Integer> rangeInclusive(int max) {
        return rangeInclusive(0, max);
    }

    private List<Integer> rangeExclusive(int min, int max) {
        return rangeExclusive(min, 1, max);
    }

    private List<Integer> rangeExclusive(int max) {
        return rangeExclusive(0, max);
    }

    private Function<String, String> wrapPrint(String pfx) {
        return x -> pfx + encodeStringToAlphanumeric(x);
    }

    private String addSimpleFunc(Collection<String> pats,
                                 Collection<String> vals,
                                 String args,
                                 String outType,
                                 String funcName,
                                 String matchVal) {
        SyntaxBuilder matchSB = new SyntaxBuilder();
        matchSB.addMatch(matchVal,
                         pats.stream().collect(Collectors.toList()),
                         vals.stream().collect(Collectors.toList()));

        String letName = String.format("%s(%s) : %s",
                                       funcName, args, outType);

        SyntaxBuilder output = new SyntaxBuilder();
        output.addGlobalLet(letName, matchSB.toString());

        return output.toString();
    }

    private String addSimpleFunc(Collection<String> pats,
                                 Collection<String> vals,
                                 String inType,
                                 String outType,
                                 String funcName) {
        String varName = String.valueOf(inType.charAt(0));
        String arg = String.format("%s: %s", varName, inType);
        return addSimpleFunc(pats, vals, arg, outType, funcName, varName);
    }

    private <T> String addOrderFunc(Collection<T> elems,
                                    Function<T, String> print,
                                    String pfx,
                                    String tyName) {
        String fnName = String.format("order_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(print)
                                 .map(wrapPrint(pfx))
                                 .collect(Collectors.toList());
        List<String> vals = rangeInclusive(pats.size()).stream()
                                                       .map(x -> Integer.toString(x))
                                                       .collect(Collectors.toList());

        return addSimpleFunc(pats, vals, tyName, "int", fnName);
    }

    private String addType(Collection<String> cons, String tyName) {
        SyntaxBuilder sb = new SyntaxBuilder();
        sb.beginTypeDefinition(tyName);
        for(String c : cons) {
            sb.addConstructor(c);
        }
        sb.endTypeDefinition();
        return sb.toString();
    }

    private <T> String addEnumType(Collection<T> toEnum,
                                   Function<T, String> print,
                                   String pfx,
                                   String tyName) {
        List<String> cons = toEnum.stream()
                                  .map(print)
                                  .map(wrapPrint(pfx))
                                  .collect(Collectors.toList());
        return addType(cons, tyName);
    }

    private void addSortOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedSorts, x -> x.name(), "Sort", "sort"));
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

    private void addKLabelType(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addEnumType(ppk.definedKLabels, x -> x.name(), "Lbl", "klabel"));
    }

    private void addKLabelOrderFunc(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addOrderFunc(ppk.definedKLabels, x -> x.name(), "Lbl", "klabel"));
    }


    private <T> String addPrinterFunc(Collection<T> elems,
                                      Function<T, String> patPrint,
                                      Function<T, String> valPrint,
                                      String nameFmt,
                                      String pfx,
                                      String tyName) {
        String fnName = String.format(nameFmt, tyName);

        List<String> pats = elems.stream()
                                 .map(patPrint)
                                 .map(wrapPrint(pfx))
                                 .collect(Collectors.toList());

        List<String> vals = elems.stream()
                                 .map(valPrint)
                                 .collect(Collectors.toList());

        return addSimpleFunc(pats, vals, tyName, "string", fnName);
    }

    private <T> String addPrintStringFunc(Collection<T> elems,
                                          Function<T, String> print,
                                          String pfx,
                                          String tyName) {
        return addPrinterFunc(elems,
                              print,
                              print.andThen(StringUtil::enquoteKString)
                                   .andThen(StringUtil::enquoteCString),
                              "print_%s_string", pfx, tyName);
    }

    private <T> String addPrintFunc(Collection<T> elems,
                                    Function<T, String> patPrint,
                                    Function<T, String> valPrint,
                                    String pfx,
                                    String tyName) {
        return addPrinterFunc(elems,
                              patPrint,
                              valPrint.andThen(StringUtil::enquoteCString),
                              "print_%s", pfx, tyName);
    }

    private void addPrintSortString(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintStringFunc(ppk.definedSorts, x -> x.name(), "Sort", "sort"));
    }

    private void addPrintSort(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedSorts, x -> x.name(), x -> x.name(), "Sort", "sort"));
    }

    private void addPrintKLabel(PreprocessedKORE ppk, SyntaxBuilder sb) {
        sb.append(addPrintFunc(ppk.definedKLabels, x -> x.name(), x -> ToKast.apply(x), "Lbl", "klabel"));
    }

    private void addRules(PreprocessedKORE ppk, SyntaxBuilder sb) {
        int i = 0;
        for (List<KLabel> component : ppk.functionOrder) {
            boolean inLetrec = false;
            sb.beginLetrecExpression();
            sb.beginLetrecDefinitions();
            for (KLabel functionLabel : component) {
                if(inLetrec) { sb.addLetrecEquationSeparator(); }
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
        sb.appendf("rule %s requires %s ensures %s %s",
                   ToKast.apply(r.body()),
                   ToKast.apply(r.requires()),
                   ToKast.apply(r.ensures()),
                   r.att().toString());
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
        FuncVisitor visitor = oldConvert(ppk, false, vars, false);

        if(function) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            sb.append(visitor.apply(kapp.klist().items(), true));
        } else {
            sb.append(visitor.apply(left));
        }

        String result = oldConvert(vars);

        if(ppk.indexedRules.get(r).contains("lookup")) {
            sb.appendf(" when not (Guard.mem (GuardElt.Guard %s) guards)",
                       Integer.toString(ruleNum));
        }

        String suffix = "";

        if(!(KSequence(BooleanUtils.TRUE).equals(requires)) || !("true".equals(result))) {
            suffix = oldConvertLookups(ppk, sb, requires, vars, functionName, ruleNum);
            sb.appendf(" when %s && (%s)",
                       oldConvert(ppk, true, vars, true).apply(requires),
                       result);
        }

        sb.append(" -> ");
        sb.append(oldConvert(ppk, true, vars, false).apply(right));
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
                sb.append(oldConvert(ppk, true, vars, false).apply(sndKLabel));
                sb.append(" with ");
                sb.addNewline();
                sb.append(str1);
                sb.append(oldConvert(ppk, false, vars, false).apply(fstKLabel));
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
                                   boolean rhs,
                                   SetMultimap<KVariable, String> vars,
                                   boolean useNativeBooleanExp) {
        return new FuncVisitor(ppk, rhs, vars, useNativeBooleanExp);
    }
}
