// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Lists;

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
import org.kframework.kore.KRewrite;
import org.kframework.kore.KToken;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.kore.AbstractKORETransformer;
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
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Comparator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.kframework.attributes.Att;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.backend.java.kore.compile.ExpandMacros;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kil.FloatBuiltin;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.Assoc;
import org.kframework.kore.AttCompare;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.DeconstructIntegerAndFloatLiterals;
import org.kframework.kore.compile.GenerateSortPredicateRules;
import org.kframework.kore.compile.LiftToKSequence;
import org.kframework.kore.compile.NormalizeVariables;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.mpfr.BigFloat;
import org.kframework.utils.StringUtil;
import org.kframework.utils.algorithms.SCCTarjan;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Function1;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Main class for converting KORE to functional code
 *
 * @author Remy Goldschmidt
 */
public class DefinitionToFunc {
    public static final boolean ocamlopt = false;
    public static final boolean commentsEnabled = false;
    public static final Pattern identChar = Pattern.compile("[A-Za-z0-9_]");

    private final KExceptionManager kem;
    private final SyntaxBuilder ocamlDefinition;
    private final SyntaxBuilder ocamlConstants;

    // private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
    //
    // static {
    //     setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
    //                              "e", "result");
    //     mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
    //                              "k", "v", "result");
    // }

    /**
     * Constructor for DefinitionToFunc
     */
    public DefinitionToFunc(KExceptionManager kem,
                            PreprocessedKORE preproc) {
        this.kem = kem;
        this.ocamlDefinition = genDefinition(preproc);
        this.ocamlConstants  = FuncConstants.genConstants(preproc);
        debugOutput(preproc);
    }

    public String getConstants() {
        return ocamlConstants.toString();
    }

    public String getDefinition() {
        return ocamlDefinition.toString();
    }

    private void debugOutput(PreprocessedKORE ppk) {
        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");

        outprintfln(";; %s", ocamlDefinition.trackPrint()
                                            .replaceAll(newline(), newline() + ";; "));
        outprintfln(";; Number of parens: %d", ocamlDefinition.getNumParens());
        outprintfln(";; Number of lines:  %d", ocamlDefinition.getNumLines());


        outprintfln("functionSet: %s", ppk.functionSet);
        outprintfln("anywhereSet: %s", ppk.anywhereSet);

        XMLBuilder outXML = ocamlDefinition.getXML();

        outprintfln("");

        try {
            outprintfln("%s", outXML.renderSExpr());
        } catch(KEMException e) {
            outprintfln(";; %s", outXML.toString()
                        .replaceAll("><", ">" + newline() + "<")
                        .replaceAll(newline(), newline() + ";; "));
        }

        outprintfln("");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        outprintfln("");
    }

    public SyntaxBuilder genDefinition(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        Module mm = ppk.mainModule;

        sb.append(genImports());
        SetMultimap<KLabel, Rule> functionRules = HashMultimap.create();
        ListMultimap<KLabel, Rule> anywhereRules = ArrayListMultimap.create();
        for(Rule r : iterable(mm.rules())) {
            K left = RewriteToTop.toLeft(r.body());
            if(left instanceof KSequence) {
                KSequence kseq = (KSequence) left;
                if(kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                    KApply kapp = (KApply) kseq.items().get(0);
                    if(mm.attributesFor().apply(kapp.klabel()).contains(Attribute.FUNCTION_KEY)) {
                        functionRules.put(kapp.klabel(), r);
                    }
                }
            }
        }

        String conn = "let rec ";
        for(KLabel functionLabel : ppk.functionSet) {
            sb.append(conn);
            String functionName = encodeStringToFunction(functionLabel.name());
            sb.appendf("%s (c: k list) (config: k) (guards: Guard.t) : k = ",
                       functionName);
            sb.appendf("let lbl = %s and sort = %s",
                       encodeStringToIdentifier(functionLabel),
                       encodeStringToIdentifier(mm.sortFor()
                                                  .apply(functionLabel)));
            String sortHook = "";
            if(mm.attributesFor()
                 .apply(functionLabel)
                 .contains(Attribute.PREDICATE_KEY)) {
                Sort sort = Sort(mm.attributesFor()
                                   .apply(functionLabel)
                                   .<String>get(Attribute.PREDICATE_KEY)
                                   .get());
                sb.appendf(" and pred_sort = %s",
                           encodeStringToIdentifier(sort));
                if(mm.sortAttributesFor().contains(sort)) {
                    sortHook = mm.sortAttributesFor()
                                 .apply(sort)
                                 .<String>getOptional("hook")
                                 .orElse("");
                }
            }

            sb.append(" in match c with ");
            sb.append(newline());

            String hook = mm.attributesFor()
                            .apply(functionLabel)
                            .<String>getOptional(Attribute.HOOK_KEY)
                            .orElse(".");
            String namespace = hook.substring(0, hook.indexOf('.'));
            String function = hook.substring(namespace.length() + 1);

            if(hookNamespaces.contains(namespace)) {
                sb.append("| _ -> try ");
                sb.appendf("%s.hook_%s", namespace, function);
                sb.appendf(" c lbl sort config freshFunction%n");
                sb.appendf("with Not_implemented -> match c with %n");
            } else if(!".".equals(hook)) {
                kem.registerCompilerWarning("missing entry for hook " + hook);
            }

            if(predicateRules.containsKey(sortHook)) {
                sb.append("| ");
                sb.append(predicateRules.get(sortHook));
                sb.appendf("%n");
            }

            sb.append(convertFunction(ppk,
                                      functionRules
                                      .get(functionLabel)
                                      .stream()
                                      .sorted(DefinitionToFunc::sortFunctionRules)
                                      .collect(toListC()),
                                      functionName,
                                      RuleType.FUNCTION));
            sb.appendf("| _ -> raise (Stuck [KApply(lbl, c)])%n");
            conn = "and ";
        }


        for(KLabel functionLabel : ppk.anywhereSet) {
            sb.append(conn);
            String functionName = encodeStringToFunction(functionLabel.name());
            sb.appendf(" (c: k list) (config: k) (guards: Guard.t) : k = let lbl = %n");
            sb.append(encodeStringToIdentifier(functionLabel));
            sb.appendf(" in match c with %n");
            sb.append(convertFunction(ppk,
                                      anywhereRules.get(functionLabel),
                                      functionName,
                                      RuleType.ANYWHERE));
            sb.appendf("| _ -> [KApply(lbl, c)]%n");
            conn = "and ";
        }


        sb.appendf("and freshFunction (sort: string) (config: k) (counter: Z.t) : k = match sort with %n");
        for(Sort sort : iterable(mm.freshFunctionFor().keys())) {
            sb.append("| \"");
            sb.append(sort.name());
            sb.append("\" -> (");
            KLabel freshFunction = mm.freshFunctionFor().apply(sort);
            sb.append(encodeStringToFunction(freshFunction.name()));
            sb.appendf(" ([Int counter] :: []) config Guard.empty)%n");
        }


        sb.appendf("and eval (c: kitem) (config: k) : k = match c with KApply(lbl, kl) -> (match lbl with %n");
        for(KLabel label : Sets.union(ppk.functionSet, ppk.anywhereSet)) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(label));
            sb.append(" -> ");
            sb.append(encodeStringToFunction(label.name()));
            sb.appendf(" kl config Guard.empty%n");
        }


        sb.appendf("| _ -> [c])%n");
        sb.appendf("| _ -> [c]%n");
        sb.appendf("let rec lookups_step (c: k) (config: k) (guards: Guard.t) : k = (match c with %n");
        List<Rule> sortedRules =
            stream(mm.rules())
            .sorted((r1, r2) ->
                    ComparisonChain
                    .start()
                    .compareTrueFirst(r1.att().contains("structural"),
                                      r2.att().contains("structural"))
                    .compareFalseFirst(r1.att().contains("owise"),
                                       r2.att().contains("owise"))
                    .compareFalseFirst(indexesPoorly(ppk, r1),
                                       indexesPoorly(ppk, r2))
                    .result())
            .filter(r -> !functionRules.values().contains(r)
                    && !r.att().contains(Attribute.MACRO_KEY)
                    && !r.att().contains(Attribute.ANYWHERE_KEY))
            .collect(toListC());
        Map<Boolean, List<Rule>> groupedByLookup =
            sortedRules
            .stream()
            .collect(groupingByC(DefinitionToFunc::hasLookups));
        Pair<Integer, SyntaxBuilder> lookup = convert(ppk,
                                                      groupedByLookup.get(true),
                                                      "lookups_step",
                                                      RuleType.REGULAR,
                                                      0);
        sb.append(lookup.getRight());
        sb.appendf("| _ -> raise (Stuck c))%n");
        sb.appendf("let step (c: k) : k = let config = c in (match c with %n");
        if(groupedByLookup.containsKey(false)) {
            for(Rule r : groupedByLookup.get(false)) {
                sb.append(convert(ppk, r, RuleType.REGULAR));
                if(ppk.fastCompilation) {
                    sb.appendf("| _ -> match c with %n");
                }
            }
        }
        sb.appendf("| _ -> lookups_step c c Guard.empty%n)");
        sb.append(OCamlIncludes.postludeSB);
        return sb;
    }

    public static SyntaxBuilder runtimeFunction(PreprocessedKORE ppk,
                                                String name,
                                                Rule r) {
        return convertFunction(ppk,
                               singletonList(ppk.convertRuntime(r)),
                               name,
                               RuleType.PATTERN);

    }



    private static SyntaxBuilder convertFunction(PreprocessedKORE ppk,
                                                 List<Rule> rules,
                                                 String functionName,
                                                 RuleType type) {
        SyntaxBuilder sb = newsb();
        int ruleNum = 0;
        for(Rule r : rules) {
            if(hasLookups(r)) {
                Pair<Integer, SyntaxBuilder> pair;
                pair = convert(ppk, Collections.singletonList(r),
                               functionName, type, ruleNum);
                ruleNum = pair.getLeft();
                sb.append(pair.getRight());
            } else {
                sb.append(convert(ppk, r, type));
            }
            if(ppk.fastCompilation) {
                sb.appendf("| _ -> match c with %n");
            }
        }
        return sb;
    }

    private static boolean hasLookups(Rule r) {
        class Holder { boolean b; }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                h.b |= isLookupKLabel(k);
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.b;
    }

    private static int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"),
                               a2.att().contains("owise"));
    }

    private static List<List<KLabel>> sortFunctions(SetMultimap<KLabel, Rule> functionRules) {
        BiMap<KLabel, Integer> mapping = HashBiMap.create();
        int counter = 0;
        HashSet<KLabel> functions = new HashSet<KLabel>(functionRules.keySet());
        for(KLabel lbl : functions) {
            mapping.put(lbl, counter++);
        }
        List<Integer>[] predecessors = new List[functions.size()];
        for(int i = 0; i < predecessors.length; i++) {
            predecessors[i] = new ArrayList<>();
        }

        class GetPredecessors extends VisitKORE {
            private final KLabel current;

            public GetPredecessors(KLabel current) {
                this.current = current;
            }

            @Override
            public Void apply(KApply k) {
                if(functions.contains(k.klabel())) {
                    predecessors[mapping.get(current)].add(mapping.get(k.klabel()));
                }
                return super.apply(k);
            }
        }

        for(Map.Entry<KLabel, Rule> entry : functionRules.entries()) {
            GetPredecessors visitor = new GetPredecessors(entry.getKey());
            visitor.apply(entry.getValue().body());
            visitor.apply(entry.getValue().requires());
        }

        List<List<Integer>> components = new SCCTarjan().scc(predecessors);

        return components.stream().map(l -> l.stream()
                .map(i -> mapping.inverse().get(i)).collect(toListC()))
                .collect(toListC());
    }

    private static enum RuleType {
        FUNCTION, ANYWHERE, REGULAR, PATTERN
    }

    private static <A,B,C,D> Tuple4<A,B,C,D> entryTuple4(Map.Entry<Triple<A,B,C>,D> e) {
        Triple<A,B,C> k = e.getKey();
        return Tuple4.apply(k.getLeft(),
                            k.getMiddle(),
                            k.getRight(),
                            e.getValue());
    }

    private static Pair<Integer, SyntaxBuilder> convert(PreprocessedKORE ppk,
                                                        List<Rule> rules,
                                                        String functionName,
                                                        RuleType ruleType,
                                                        int ruleNum) {
        SyntaxBuilder sb = newsb();
        int rn = ruleNum;

        NormalizeVariables t = new NormalizeVariables();

        Function<Rule, AttCompare> groupingFunc = r -> {
            return new AttCompare(t.normalize(RewriteToTop.toLeft(r.body())),
                                  "sort");
        };

        Map<AttCompare, List<Rule>>
            grouping = rules.stream().collect(groupingByC(groupingFunc));

        Map<Triple<AttCompare, KLabel, AttCompare>, List<Rule>>
            groupByFirstPrefix = new HashMap<>();

        for(Map.Entry<AttCompare, List<Rule>> entry : grouping.entrySet()) {
            AttCompare left = entry.getKey();

            Function<Rule, Triple<AttCompare, KLabel, AttCompare>> tempRule = r -> {
                KApply lookup = getLookup(r, 0);
                if(lookup == null) { return null; }
                //reconstruct the denormalization for this particular rule
                K left2 = t.normalize(RewriteToTop.toLeft(r.body()));
                K normal = t.normalize(t.applyNormalization(lookup.klist()
                                                                  .items()
                                                                  .get(1),
                                                            left2));
                return Triple.of(left,
                                 lookup.klabel(),
                                 new AttCompare(normal, "sort"));
            };

            groupByFirstPrefix.putAll(entry.getValue()
                                           .stream()
                                           .collect(groupingByC(tempRule)));
        }

        List<Rule> owiseRules = newArrayList();

        // Comparator<Map.Entry<?, List<?>>> sorter = (e1, e2) -> {
        Comparator<Map.Entry<Triple<AttCompare,KLabel,AttCompare>,List<Rule>>> sorter;
        sorter = (e1, e2) -> {
            return Integer.compare(e2.getValue().size(),
                                   e1.getValue().size());
        };

        for(Tuple4<AttCompare, KLabel, AttCompare, List<Rule>> entry2
                : groupByFirstPrefix.entrySet()
                                    .stream()
                                    .sorted(sorter)
                                    .map(DefinitionToFunc::entryTuple4)
                                    .collect(toListC())) {

            K left = entry2._1().get();
            FuncVisitor.VarInfo globalVars = new FuncVisitor.VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk, ruleType, left, globalVars));
            K lookup;
            sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", rn);
            if(entry2._4().size() == 1) {
                Rule r = entry2._4().get(0);
                sb.append(convertComment(r));

                left = t.normalize(RewriteToTop.toLeft(r.body()));
                lookup = t.normalize(t.applyNormalization(getLookup(r, 0)
                                                          .klist()
                                                          .items()
                                                          .get(1),
                                                          left));
                r = t.normalize(t.applyNormalization(r, left, lookup));

                List<Lookup> lookups = convertLookups(ppk,
                                                      globalVars,
                                                      r.requires(),
                                                      functionName,
                                                      rn,
                                                      false);

                SBPair pair = convertSideCondition(ppk,
                                                   r.requires(),
                                                   globalVars,
                                                   lookups,
                                                   ! lookups.isEmpty());
                sb.append(pair.getPrefix());
                sb.append(" -> ");
                sb.append(convertRHS(ppk,
                                     ruleType,
                                     RewriteToTop.toRight(r.body()),
                                     globalVars));
                sb.append(pair.getSuffix());
                sb.addNewline();
                rn++;
            } else {
                KToken dummy = KToken("dummy", Sort("Dummy"));
                KApply kapp  = KApply(entry2._2(), dummy, entry2._3().get());
                List<Lookup> lookupList  = convertLookups(ppk,
                                                          globalVars,
                                                          kapp,
                                                          functionName,
                                                          rn++,
                                                          true);
                sb.append(lookupList.get(0).prefix);
                for(Rule r : entry2._4()) {
                    if(indexesPoorly(ppk, r) || r.att().contains("owise")) {
                        owiseRules.add(r);
                    } else {
                        sb.append(convertComment(r));

                        //reconstruct the denormalization for this particular rule
                        left = t.normalize(RewriteToTop.toLeft(r.body()));
                        lookup = t.normalize(t.applyNormalization(getLookup(r, 0).klist()
                                                                                 .items()
                                                                                 .get(1),
                                                                  left));
                        r = t.normalize(t.applyNormalization(r, left, lookup));

                        FuncVisitor.VarInfo vars = new FuncVisitor.VarInfo(globalVars);
                        List<Lookup> lookups = convertLookups(ppk,
                                                              vars,
                                                              r.requires(),
                                                              functionName,
                                                              rn,
                                                              true);
                        sb.append(lookups.get(0).pattern);
                        lookups.remove(0);
                        sb.appendf(" when not (Guard.mem (GuardElt.Guard %d) guards)", rn);
                        SBPair pair = convertSideCondition(ppk,
                                                           r.requires(),
                                                           vars,
                                                           lookups,
                                                           lookups.size() > 0);
                        sb.append(pair.getPrefix());
                        sb.append(" -> ");
                        sb.append(convertRHS(ppk,
                                             ruleType,
                                             RewriteToTop.toRight(r.body()),
                                             vars));
                        sb.append(pair.getSuffix());
                        sb.addNewline();
                        rn++;
                        if(ppk.fastCompilation) {
                            sb.appendf("| _ -> match e with %n");
                        }
                    }
                }
                sb.append(lookupList.get(0).suffix);
                sb.appendf("%n");
            }
        }

        for(Rule r : owiseRules) {
            FuncVisitor.VarInfo globalVars = new FuncVisitor.VarInfo();
            sb.append("| ");
            sb.append(convertLHS(ppk,
                                 ruleType,
                                 RewriteToTop.toLeft(r.body()),
                                 globalVars));
            sb.append(" when not (Guard.mem (GuardElt.Guard ");
            sb.addInteger(rn);
            sb.append(") guards)");

            sb.append(convertComment(r));
            List<Lookup> lookups = convertLookups(ppk,
                                                  globalVars,
                                                  r.requires(),
                                                  functionName,
                                                  rn,
                                                  false);
            SBPair pair = convertSideCondition(ppk,
                                               r.requires(),
                                               globalVars,
                                               lookups,
                                               ! lookups.isEmpty());
            sb.append(" -> ");
            sb.append(convertRHS(ppk,
                                 ruleType,
                                 RewriteToTop.toRight(r.body()),
                                 globalVars));
            sb.append(pair.getSuffix());
            sb.addNewline();
            rn++;
        }
        return Pair.of(rn, sb);
    }

    private static boolean indexesPoorly(PreprocessedKORE ppk,
                                         Rule r) {
        Module mm = ppk.mainModule;
        class Holder { boolean b; }
        Holder h = new Holder();
        VisitKORE visitor = new VisitKORE() {
                @Override
                public Void apply(KApply k) {
                    if(   k.klabel().name().equals("<k>")
                       && k.klist().items().size() == 1
                       && k.klist().items().get(0) instanceof KSequence) {
                        KSequence kCell = (KSequence) k.klist().items().get(0);
                        if(kCell.items().size() == 2 && kCell.items().get(1) instanceof KVariable) {
                            if(kCell.items().get(0) instanceof KVariable) {
                                Sort s = Sort(kCell.items().get(0).att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
                                if(mm.sortAttributesFor().contains(s)) {
                                    String hook = mm.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
                                    if(!sortVarHooks.containsKey(hook)) {
                                        h.b = true;
                                    }
                                } else {
                                    h.b = true;
                                }
                                } else if(kCell.items().get(0) instanceof KApply) {
                                KApply kapp = (KApply) kCell.items().get(0);
                                if(kapp.klabel() instanceof KVariable) {
                                    h.b = true;
                                }
                            }
                        }
                    }
                    return super.apply(k);
                }
            };
        visitor.apply(RewriteToTop.toLeft(r.body()));
        visitor.apply(r.requires());
        return h.b;
    }

    private static KApply getLookup(Rule r, int idx) {
        class Holder {
            int i = 0;
            KApply lookup;
        }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if(h.i > idx) { return null; }
                if(isLookupKLabel(k)) {
                    h.lookup = k;
                    h.i++;
                }
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.lookup;
    }

    private static SyntaxBuilder convert(PreprocessedKORE ppk,
                                         Rule r,
                                         RuleType type) {
        try {
            SyntaxBuilder sb = newsb();
            sb.append(convertComment(r));
            sb.append("| ");
            K left = RewriteToTop.toLeft(r.body());
            K right = RewriteToTop.toRight(r.body());
            K requires = r.requires();
            FuncVisitor.VarInfo vars = new FuncVisitor.VarInfo();
            sb.append(convertLHS(ppk, type, left, vars));
            SyntaxBuilder prefix = FuncVisitor.convert(ppk, vars);
            SyntaxBuilder suffix = newsb();
            if(!requires.equals(KSequence(BooleanUtils.TRUE)) ||
               !"true".equals(prefix.toString())) {
                SBPair pair = convertSideCondition(ppk,
                                                   requires,
                                                   vars,
                                                   Collections.emptyList(),
                                                   true);
                sb.append(pair.getPrefix());
                suffix.append(pair.getSuffix());
            }
            sb.append(" -> ");
            sb.append(convertRHS(ppk, type, right, vars));
            sb.append(suffix);
            sb.addNewline();
            return sb;
        } catch (KEMException e) {
            String src = r.att()
                          .getOptional(Source.class)
                          .map(Object::toString)
                          .orElse("<none>");
            String loc = r.att()
                          .getOptional(Location.class)
                          .map(Object::toString)
                          .orElse("<none>");
            e.exception
             .addTraceFrame(String.format("while compiling rule at %s: %s",
                                          src, loc));
            throw e;
        }
    }

    private static SyntaxBuilder convertComment(Rule r) {
        if(commentsEnabled) {
            return newsb()
                .addComment(String.format("rule %s requires %s ensures %s | %s",
                                          ToKast.apply(r.body()),
                                          ToKast.apply(r.requires()),
                                          ToKast.apply(r.ensures()),
                                          r.att()));
        } else {
            return newsb();
        }
    }

    private static SyntaxBuilder convertLHS(PreprocessedKORE ppk,
                                            RuleType type,
                                            K left,
                                            FuncVisitor.VarInfo vars) {
        FuncVisitor visitor = genVisitor(ppk, vars, false, false, false);
        if(type == RuleType.ANYWHERE || type == RuleType.FUNCTION) {
            KApply kapp = (KApply) ((KSequence) left).items().get(0);
            return visitor.apply(kapp.klist().items(), true);
        } else {
            return visitor.apply(left);
        }
    }

    private static SyntaxBuilder convertRHS(PreprocessedKORE ppk,
                                            RuleType type,
                                            K right,
                                            FuncVisitor.VarInfo vars) {
        SyntaxBuilder sb = newsb();
        boolean isPat = type == RuleType.PATTERN;
        boolean isAny = type == RuleType.ANYWHERE;

        if(isPat) {
            for(KVariable var : vars.getVars().keySet()) {
                sb.beginApplication();
                sb.addFunction("Subst.add");
                sb.addArgument(newsbStr(var.name()));
                String fmt = FuncVisitor.isList(var, ppk, vars,
                                                true, false) ? "%s" : "[%s]";
                sb.addArgument(newsbv(String.format(fmt, vars.getVars()
                                                             .get(var)
                                                             .iterator()
                                                             .next())));
            }
            sb.addArgument(newsbv("Subst.empty"));
            for(KVariable var : vars.getVars().keySet()) {
                sb.endApplication();
            }
        } else {
            sb.append(genVisitor(ppk, vars, true, false, isAny).apply(right));
        }

        if(isAny) {
            sb = newsb().addMatchSB(sb,
                                    asList(newsbp("[item]")),
                                    asList(newsbApp("eval",
                                                    newsbn("item"),
                                                    newsbn("config"))));
        }

        return sb;
    }

    private static SBPair convertSideCondition(PreprocessedKORE ppk,
                                               K requires,
                                               FuncVisitor.VarInfo vars,
                                               List<Lookup> lus,
                                               boolean when) {
        SyntaxBuilder prefix, suffix;
        prefix = newsb();
        for(Lookup lookup : lus) {
            prefix.append(lookup.prefix);
            prefix.append(lookup.pattern);
        }
        SyntaxBuilder temp = FuncVisitor.convert(ppk, vars);
        prefix.addSpace();
        prefix.addKeyword(when ? "when" : "&&");
        prefix.addSpace();
        prefix.append(genVisitor(ppk, vars, true, true, false).apply(requires));
        prefix.addSpace();
        prefix.addKeyword("&&");
        prefix.addSpace();
        prefix.append(temp);
        suffix = newsb(Lists
                       .reverse(lus)
                       .stream()
                       .map(l -> l.suffix)
                       .collect(joiningC()));
        return newSBPair(prefix, suffix);
    }

    private static class Holder { String reapply; boolean first; }

    private static class Lookup {
        final String prefix;
        final String pattern;
        final String suffix;

        public Lookup(String prefix, String pattern, String suffix) {
            this.prefix = prefix;
            this.pattern = pattern;
            this.suffix = suffix;
        }
    }

    private static List<Lookup> convertLookups(PreprocessedKORE ppk,
                                               FuncVisitor.VarInfo vars,
                                               K requires,
                                               String functionName,
                                               int ruleNum,
                                               boolean hasMultiple) {
        List<Lookup> results = new ArrayList<>();
        Holder h = new Holder();
        h.first = hasMultiple;
        h.reapply = "(" + functionName + " c config (Guard.add (GuardElt.Guard " + ruleNum + ") guards))";
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                Optional<Lookup> app = convertLookupsApply(ppk, vars, k,
                                                           ruleNum, h);
                app.ifPresent(results::add);
                return super.apply(k);
            }
        }.apply(requires);
        return results;
    }

    private static Optional<Lookup> convertLookupsApply(PreprocessedKORE ppk,
                                                        FuncVisitor.VarInfo vars,
                                                        KApply k,
                                                        int rn,
                                                        Holder hold) {
        if(k.klabel().name().equals("#match")) {
            if(k.klist().items().size() != 2) {
                throw kemInternalErrorF(k, "Unexpected arity of lookup: %s",
                                        k.klist().size());
            }
            SyntaxBuilder sb = newsb();
            sb.append(" -> (let e = ");
            sb.append(genVisitor(ppk, vars, true, false, false).apply(k.klist()
                                                                       .items()
                                                                       .get(1)));
            sb.appendf(" in match e with %n");
            sb.append("| [Bottom] -> ");
            sb.append(hold.reapply);
            sb.appendf("%n");
            String prefix = sb.toString();
            sb = newsb();
            sb.append("| ");
            sb.append(genVisitor(ppk, vars, false, false, false).apply(k.klist()
                                                                        .items()
                                                                        .get(0)));
            String pattern = sb.toString();
            String suffix = "| _ -> " + hold.reapply + ")";
            hold.first = false;
            return Optional.of(new Lookup(prefix, pattern, suffix));
        } else if(k.klabel().name().equals("#setChoice")) {
            return convertLookupsChoose(k, ppk, vars, hold, rn, "Set", "e");
        } else if(k.klabel().name().equals("#mapChoice")) {
            return convertLookupsChoose(k, ppk, vars, hold, rn, "Map", "e v");
        }
        return Optional.empty();
    }

    private static Optional<Lookup> convertLookupsChoose(KApply k,
                                                         PreprocessedKORE ppk,
                                                         FuncVisitor.VarInfo vars,
                                                         Holder hold,
                                                         int rn,
                                                         String collectionName,
                                                         String collectionVars) {
        String formatString =
            "| [%s (_,_,collection)] -> let choice = (K%s.fold (fun %s result -> ";

        String choiceString = String.format(formatString,
                                            collectionName,
                                            collectionName,
                                            collectionVars);

        if(k.klist().items().size() != 2) {
            throw kemInternalErrorF(k, "Unexpected arity of choice: %s",
                                    k.klist().size());
        }

        SyntaxBuilder sb = newsb();
        sb.append(" -> (match ");
        sb.append(genVisitor(ppk, vars, true, false, false).apply(k.klist()
                                                                  .items()
                                                                  .get(1)));
        sb.appendf(" with %n");
        sb.append(choiceString);
        if(hold.first) {
            sb.append("let rec stepElt = fun guards -> ");
        }
        sb.append("if (compare result [Bottom]) = 0 then (match e with ");
        String prefix = sb.toString();
        sb = newsb();
        String suffix2 = String.format("| _ -> [Bottom]) else result%s) collection [Bottom]) in if (compare choice [Bottom]) = 0 then %s else choice",
                                       (hold.first ? " in stepElt Guard.empty" : ""),
                                       hold.reapply);
        String suffix = String.format("%s| _ -> %s)", suffix2, hold.reapply);
        if(hold.first) {
            hold.reapply = String.format("(stepElt (Guard.add (GuardElt.Guard %d) guards))", rn);
        } else {
            hold.reapply = "[Bottom]";
        }
        sb.append("| ");
        sb.append(genVisitor(ppk, vars, false, false, false).apply(k.klist()
                                                                   .items()
                                                                   .get(0)));
        String pattern = sb.toString();
        hold.first = false;
        return Optional.of(new Lookup(prefix, pattern, suffix));
    }

    /**
     * Create a Visitor based on the given information
     */
    private static FuncVisitor genVisitor(PreprocessedKORE ppk,
                                          FuncVisitor.VarInfo vars,
                                          boolean rhs,
                                          boolean useNativeBool,
                                          boolean anywhereRule) {
        return new FuncVisitor(ppk, vars, rhs, useNativeBool, anywhereRule);
    }

    /**
     * Does the given KApply have a lookup-related label?
     * @param k     The KApply to test
     * @return      Whether or not the given KApply has a lookup-related label
     */
    private static boolean isLookupKLabel(KApply k) {
        return k.klabel().name().equals("#match")
            || k.klabel().name().equals("#mapChoice")
            || k.klabel().name().equals("#setChoice");
    }

    private static SBPair newSBPair(SyntaxBuilder pre,
                                    SyntaxBuilder suff) {
        return new SBPair(pre, suff);
    }

    private static class SBPair {
        private final SyntaxBuilder prefix, suffix;

        public SBPair(SyntaxBuilder prefix, SyntaxBuilder suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }


        public SyntaxBuilder getPrefix() {
            return prefix;
        }

        public SyntaxBuilder getSuffix() {
            return suffix;
        }
    }
}


// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------

//     /**
//      * Flag that determines whether or not we annotate output OCaml with rules
//      */
//     public static final boolean annotateOutput = false;

//     /**
//      * Flag that determines whether or not we output debug info
//      */
//     public static final boolean debugEnabled = true;


//     private final KExceptionManager kem;
//     private final SyntaxBuilder ocamlDef;

//     private static final SyntaxBuilder setChoiceSB1, mapChoiceSB1;
//     private static final SyntaxBuilder wildcardSB, bottomSB, choiceSB, resultSB;

//     static {
//         wildcardSB = newsbv("_");
//         bottomSB = newsbv("[Bottom]");
//         choiceSB = newsbv("choice");
//         resultSB = newsbv("result");
//         setChoiceSB1 = choiceSB1("e", "KSet.fold", "[Set s]",
//                                  "e", "result");
//         mapChoiceSB1 = choiceSB1("k", "KMap.fold", "[Map m]",
//                                  "k", "v", "result");
//     }

//     /**
//      * Constructor for DefinitionToFunc
//      */
//     public DefinitionToFunc(KExceptionManager kem,
//                             PreprocessedKORE preproc) {
//         this.kem = kem;
//         this.ocamlDef = langDefToFunc(preproc);
//         debugOutput(preproc);
//     }

//     public String genOCaml() {
//         return ocamlDef.toString();
//     }

//     private void debugOutput(PreprocessedKORE ppk) {
//         outprintfln("");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln("");

//         outprintfln(";; %s", ocamlDef.trackPrint()
//                                      .replaceAll(newline(), newline() + ";; "));
//         outprintfln(";; Number of parens: %d", ocamlDef.getNumParens());
//         outprintfln(";; Number of lines:  %d", ocamlDef.getNumLines());


//         outprintfln("functionSet: %s", ppk.functionSet);
//         outprintfln("anywhereSet: %s", ppk.anywhereSet);

//         XMLBuilder outXML = ocamlDef.getXML();


//         outprintfln("");

//         try {
//             outprintfln("%s", outXML.renderSExpr());
//         } catch(KEMException e) {
//             outprintfln(";; %s", outXML.toString()
//                         .replaceAll("><", ">" + newline() + "<")
//                         .replaceAll(newline(), newline() + ";; "));
//         }


//         outprintfln("");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln(";; DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
//         outprintfln("");
//     }

//     private SyntaxBuilder langDefToFunc(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.append(FuncConstants.genConstants(ppk));
//         sb.append(addFunctions(ppk));
//         sb.append(addSteps(ppk));
//         sb.append(OCamlIncludes.postludeSB);

//         return sb;
//     }

//     private SyntaxBuilder addFunctionMatch(String functionName,
//                                            KLabel functionLabel,
//                                            PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();
//         String hook = ppk.attrLabels
//                          .get(Attribute.HOOK_KEY)
//                          .getOrDefault(functionLabel, "");
//         String fn = functionLabel.name();
//         boolean isHook = OCamlIncludes.hooks.containsKey(hook);
//         boolean isPred = OCamlIncludes.predicateRules.containsKey(fn);
//         Collection<Rule> rules = ppk.functionRulesOrdered
//                                     .getOrDefault(functionLabel, newArrayList());

//         if(!isHook && !hook.isEmpty()) {
//             kem.registerCompilerWarning("missing entry for hook " + hook);
//         }

//         sb.beginMatchExpression(newsbv("c"));

//         if(isHook) {
//             sb.append(OCamlIncludes.hooks.get(hook));
//         }

//         if(isPred) {
//             sb.append(OCamlIncludes.predicateRules.get(fn));
//         }

//         int i = 0;
//         for(Rule r : rules) {
//             sb.append(oldConvert(ppk, r, true, i++, functionName));
//         }

//         sb.addMatchEquation(wildcardSB, raiseStuck(newsbv("[KApply(lbl, c)]")));
//         sb.endMatchExpression();

//         return sb;
//     }

//     private SyntaxBuilder addFunctionEquation(KLabel functionLabel,
//                                               PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         String functionName = encodeStringToFunction(functionLabel.name());

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb()
//                                  .beginRender()
//                                  .addValue(functionName)
//                                  .addSpace()
//                                  .addValue("(c: k list)")
//                                  .addSpace()
//                                  .addValue("(guards: Guard.t)")
//                                  .addSpace()
//                                  .addKeyword(":")
//                                  .addSpace()
//                                  .addValue("k")
//                                  .endRender());
//         sb.beginLetrecEquationValue();
//         sb.beginLetExpression();
//         sb.beginLetDefinitions();
//         sb.addLetEquation(newsb("lbl"),
//                           newsb(encodeStringToIdentifier(functionLabel)));
//         sb.endLetDefinitions();

//         sb.beginLetScope();
//         sb.append(addFunctionMatch(functionName, functionLabel, ppk));
//         sb.endLetScope();

//         sb.endLetExpression();
//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addFreshFunction(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb()
//                                  .beginRender()
//                                  .addValue("freshFunction")
//                                  .addSpace()
//                                  .addValue("(sort: string)")
//                                  .addSpace()
//                                  .addValue("(counter: Z.t)")
//                                  .addSpace()
//                                  .addKeyword(":")
//                                  .addSpace()
//                                  .addValue("k")
//                                  .endRender());
//         sb.beginLetrecEquationValue();

//         sb.beginMatchExpression(newsb("sort"));
//         for(Sort sort : ppk.freshFunctionFor.keySet()) {
//             KLabel freshFunction = ppk.freshFunctionFor.get(sort);
//             sb.addMatchEquation(newsbf("\"%s\"",
//                                        sort.name()),
//                                 newsbf("(%s ([Int counter] :: []) Guard.empty)",
//                                        encodeStringToFunction(freshFunction.name())));
//         }
//         sb.endMatchExpression();

//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addEval(Set<KLabel> labels,
//                                   PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb("eval (c: kitem) : k"));
//         sb.beginLetrecEquationValue();

//         sb.beginMatchExpression(newsb("c"));

//         sb.beginMatchEquation();
//         sb.addMatchEquationPattern(newsb()
//                                    .addApplication("KApply",
//                                                    newsb("(lbl, kl)")));
//         sb.beginMatchEquationValue();
//         sb.beginMatchExpression(newsb("lbl"));
//         for(KLabel label : labels) {
//             SyntaxBuilder valSB =
//                 newsb().addApplication(encodeStringToFunction(label.name()),
//                                        newsb("kl"),
//                                        newsb("Guard.empty"));
//             sb.addMatchEquation(newsb(encodeStringToIdentifier(label)),
//                                 valSB);
//         }
//         sb.endMatchExpression();
//         sb.endMatchEquationValue();
//         sb.endMatchEquation();

//         sb.addMatchEquation(wildcardSB, newsbv("[c]"));

//         sb.endMatchExpression();

//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();

//         return sb;
//     }

//     private SyntaxBuilder addFunctions(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         Set<KLabel> functions = ppk.functionSet;
//         Set<KLabel> anywheres = ppk.anywhereSet;

//         Set<KLabel> funcAndAny = Sets.union(functions, anywheres);


//         for(List<KLabel> component : ppk.functionOrder) {
//             sb.beginLetrecDeclaration();
//             sb.beginLetrecDefinitions();
//             for(KLabel functionLabel : component) {
//                 sb.append(addFunctionEquation(functionLabel, ppk));
//             }
//             sb.endLetrecDefinitions();
//             sb.endLetrecDeclaration();
//         }

//         sb.beginLetrecDeclaration();
//         sb.beginLetrecDefinitions();

//         sb.append(addFreshFunction(ppk));

//         sb.append(addEval(funcAndAny, ppk));

//         sb.endLetrecDefinitions();
//         sb.endLetrecDeclaration();

//         return sb;
//     }

//     private SyntaxBuilder makeStuck(SyntaxBuilder body) {
//         return newsb().addApplication("Stuck", body);
//     }

//     private SyntaxBuilder raiseStuck(SyntaxBuilder body) {
//         return newsb().addApplication("raise", makeStuck(body));
//     }

//     private SyntaxBuilder addSteps(PreprocessedKORE ppk) {
//         SyntaxBuilder sb = newsb();

//         sb.beginLetrecDeclaration();
//         sb.beginLetrecDefinitions();
//         sb.beginLetrecEquation();
//         sb.addLetrecEquationName(newsb("lookups_step (c: k) (guards: Guard.t) : k"));
//         sb.beginLetrecEquationValue();
//         sb.beginMatchExpression(newsb("c"));

//         int i = 0;
//         for(Rule r : ppk.indexedRules.keySet()) {
//             Set<String> cap = ppk.indexedRules.get(r);
//             if(cap.contains("lookup") && !cap.contains("function")) {
//                 sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "lookups_step")));
//             }
//         }

//         sb.addMatchEquation(wildcardSB, raiseStuck(newsb("c")));
//         sb.endMatchExpression();
//         sb.endLetrecEquationValue();
//         sb.endLetrecEquation();
//         sb.endLetrecDefinitions();
//         sb.endLetrecDeclaration();

//         sb.beginLetDeclaration();
//         sb.beginLetDefinitions();
//         sb.beginLetEquation();
//         sb.addLetEquationName(newsb("step (c: k) : k"));
//         sb.beginLetEquationValue();
//         sb.beginMatchExpression(newsb("c"));
//         for(Rule r : ppk.indexedRules.keySet()) {
//             Set<String> cap = ppk.indexedRules.get(r);
//             if(!cap.contains("lookup") && !cap.contains("function")) {
//                 sb.append(debugMismatch(oldConvert(ppk, r, false, i++, "step")));
//             }
//         }
//         sb.addMatchEquation(newsb("_"),
//                             newsb("lookups_step c Guard.empty"));
//         sb.endMatchExpression();
//         sb.endLetEquationValue();
//         sb.endLetEquation();
//         sb.endLetDefinitions();
//         sb.endLetDeclaration();

//         return sb;
//     }

//     private SyntaxBuilder debugMismatch(SyntaxBuilder sb) {
//         Pattern xmlBegPat = Pattern.compile("<[^<>/]*>");
//         Pattern xmlEndPat = Pattern.compile("</[^<>/]*>");
//         Map<String, Integer> sbMap = sb.getTrack();
//         List<String> sbKeys = sbMap.keySet()
//                                    .stream()
//                                    .filter(x -> xmlEndPat.matcher(x).matches())
//                                    .map(x -> x.substring(2, x.length() - 1))
//                                    .collect(toListC());
//         Map<String, Integer> mismatched = newHashMap();
//         for(String k : sbKeys) {
//             int begin = sbMap.get("<"  + k + ">").intValue();
//             int end   = sbMap.get("</" + k + ">").intValue();
//             if(begin != end) { mismatched.put(k, begin - end); }
//         }
//         if(! mismatched.isEmpty()) {
//             outprintfln(";; ---------------- ERROR ----------------");
//             outprintfln(";; The following were mismatched:");
//             for(String k : mismatched.keySet()) {
//                 outprintfln(";; %30s --> %s", k, mismatched.get(k));
//             }
//             outprintfln(";; ----------------");
//             outprintfln(";; XML:%n;; %s",
//                         sb.pretty().stream().collect(joiningC(newline() + ";; ")));
//             outprintfln(";; ---------------- ERROR ----------------");
//         }
//         return sb;
//     }

//     private SyntaxBuilder outputAnnotate(Rule r) {
//         SyntaxBuilder sb = newsb();

//         sb.beginMultilineComment();
//         sb.appendf("rule %s requires %s ensures %s %s",
//                    ToKast.apply(r.body()),
//                    ToKast.apply(r.requires()),
//                    ToKast.apply(r.ensures()),
//                    r.att().toString());
//         sb.endMultilineComment();
//         sb.addNewline();

//         return sb;
//     }

//     private SyntaxBuilder unhandledOldConvert(PreprocessedKORE ppk,
//                                               Rule r,
//                                               boolean isFunction,
//                                               int ruleNum,
//                                               String functionName) throws KEMException {
//         SyntaxBuilder sb = newsb();

//         if(annotateOutput) { sb.append(outputAnnotate(r)); }

//         K left     = RewriteToTop.toLeft(r.body());
//         K right    = RewriteToTop.toRight(r.body());
//         K requires = r.requires();

//         Set<String> indices = ppk.indexedRules.get(r);
//         SetMultimap<KVariable, String> vars = HashMultimap.create();
//         FuncVisitor visitor = oldConvert(ppk, false, vars, false);

//         sb.beginMatchEquation();
//         sb.beginMatchEquationPattern();
//         sb.append(handleLeft(isFunction, left, visitor));

//         sb.append(handleLookup(indices, ruleNum));

//         SBPair side = handleSideCondition(ppk, vars, functionName, ruleNum, requires);

//         sb.append(side.getFst());
//         sb.endMatchEquationPattern();
//         sb.beginMatchEquationValue();
//         sb.append(oldConvert(ppk, true, vars, false).apply(right));

//         sb.endMatchEquationValue();
//         sb.endMatchEquation();
//         sb.append(side.getSnd());

//         return sb;
//     }

//     private SyntaxBuilder handleLeft(boolean isFunction,
//                                      K left,
//                                      FuncVisitor visitor) {
//         if(isFunction) {
//             return handleFunction(left, visitor);
//         } else {
//             return handleNonFunction(left, visitor);
//         }
//     }

//     private SyntaxBuilder handleFunction(K left, FuncVisitor visitor) {
//         KApply kapp = (KApply) ((KSequence) left).items().get(0);
//         return visitor.apply(kapp.klist().items(), true);
//     }

//     private SyntaxBuilder handleNonFunction(K left, FuncVisitor visitor) {
//         return visitor.apply(left);
//     }

//     private SyntaxBuilder handleLookup(Set<String> indices, int ruleNum) {
//         if(indices.contains("lookup")) {
//             return newsb()
//                 .beginRender()
//                 .addSpace()
//                 .addKeyword("when")
//                 .addSpace()
//                 .addKeyword("not")
//                 .addSpace()
//                 .beginApplication()
//                 .addFunction("Guard.mem")
//                 .beginArgument()
//                 .addApplication("GuardElt.Guard", newsbf("%d", ruleNum))
//                 .endArgument()
//                 .addArgument(newsb("guards"))
//                 .endApplication()
//                 .endRender();
//         } else {
//             return newsb();
//         }
//     }

//     private SBPair handleSideCondition(PreprocessedKORE ppk,
//                                        SetMultimap<KVariable, String> vars,
//                                        String functionName,
//                                        int ruleNum,
//                                        K requires) {
//          SBPair convLookups = oldConvertLookups(ppk, requires, vars,
//                                                functionName, ruleNum);

//          SyntaxBuilder result = oldConvert(vars);

//          if(hasSideCondition(requires, result.toString())) {
//              SyntaxBuilder fstSB =
//                  convLookups
//                  .getFst()
//                  .beginRender()
//                  .addSpace()
//                  .addKeyword("when")
//                  .addSpace()
//                  .beginRender()
//                  .append(oldConvert(ppk, true, vars, true).apply(requires))
//                  .endRender()
//                  .addSpace()
//                  .addKeyword("&&")
//                  .addSpace()
//                  .beginParenthesis()
//                  .beginRender()
//                  .append(result)
//                  .endRender()
//                  .endParenthesis()
//                  .endRender();
//              SyntaxBuilder sndSB = convLookups.getSnd();
//              return newSBPair(fstSB, sndSB);
//          } else {
//              return newSBPair(newsb(), newsb());
//          }
//     }

//     private boolean hasSideCondition(K requires, String result) {
//         return !(KSequence(BooleanUtils.TRUE).equals(requires))
//             || !("true".equals(result));
//     }

//     private SyntaxBuilder oldConvert(PreprocessedKORE ppk,
//                                      Rule r,
//                                      boolean function,
//                                      int ruleNum,
//                                      String functionName) {
//         try {
//             return unhandledOldConvert(ppk, r, function, ruleNum, functionName);
//         } catch (KEMException e) {
//             String src = r.att()
//                           .getOptional(Source.class)
//                           .map(Object::toString)
//                           .orElse("<none>");
//             String loc = r.att()
//                           .getOptional(Location.class)
//                           .map(Object::toString)
//                           .orElse("<none>");
//             e.exception
//              .addTraceFrame(String.format("while compiling rule at %s: %s",
//                                           src, loc));
//             throw e;
//         }
//     }

//     private void checkApplyArity(KApply k,
//                                  int arity,
//                                  String funcName) throws KEMException {
//         if(k.klist().size() != arity) {
//             throw kemInternalErrorF(k,
//                                     "Unexpected arity of %s: %s",
//                                     funcName,
//                                     k.klist().size());
//         }
//     }

//     private static final SyntaxBuilder choiceSB1(String chChoiceVar,
//                                                  String chFold,
//                                                  String chPat,
//                                                  String... chArgs) {
//         return newsb()
//             .beginMatchEquation()
//             .addMatchEquationPattern(newsbv(chPat))
//             .beginMatchEquationValue()
//             .beginLetExpression()
//             .beginLetDefinitions()
//             .beginLetEquation()
//             .addLetEquationName(choiceSB)
//             .beginLetEquationValue()
//             .beginApplication()
//             .addFunction(chFold)
//             .beginArgument()
//             .beginLambda(chArgs)
//             .beginConditional()
//             .addConditionalIf()
//             .append(newsb().addApplication("eq", resultSB, bottomSB))
//             .addConditionalThen()
//             .beginMatchExpression(newsbv(chChoiceVar));
//     }


//     private static final SyntaxBuilder choiceSB2(String chVar,
//                                                  int ruleNum,
//                                                  String functionName) {
//         String guardCon = "GuardElt.Guard";
//         String guardAdd = "Guard.add";
//         SyntaxBuilder rnsb = newsbv(Integer.toString(ruleNum));

//         SyntaxBuilder guardSB =
//             newsb().addApplication(guardAdd,
//                                    newsb().addApplication(guardCon, rnsb),
//                                    newsbv("guards"));
//         SyntaxBuilder condSB =
//             newsb().addConditional(newsb().addApplication("eq", choiceSB, bottomSB),
//                                    newsb().addApplication(functionName,
//                                                           newsbv("c"),
//                                                           guardSB),
//                                    choiceSB);

//         return newsb()
//             .addMatchEquation(wildcardSB, bottomSB)
//             .endMatchExpression()
//             .addConditionalElse()
//             .append(resultSB)
//             .endConditional()
//             .endLambda()
//             .endArgument()
//             .addArgument(newsbv(chVar))
//             .addArgument(bottomSB)
//             .endApplication()
//             .endLetEquationValue()
//             .endLetEquation()
//             .endLetDefinitions()
//             .addLetScope(condSB)
//             .endLetExpression()
//             .endMatchEquationValue()
//             .endMatchEquation();
//     }

//     // TODO(remy): this needs refactoring very badly
//     private SBPair oldConvertLookups(PreprocessedKORE ppk,
//                                      K requires,
//                                      SetMultimap<KVariable, String> vars,
//                                      String functionName,
//                                      int ruleNum) {
//         Deque<SyntaxBuilder> suffStack = new ArrayDeque<>();

//         SyntaxBuilder res = newsb();
//         SyntaxBuilder setChoiceSB2 = choiceSB2("s", ruleNum, functionName);
//         SyntaxBuilder mapChoiceSB2 = choiceSB2("m", ruleNum, functionName);
//         String formatSB3 = "(%s c (Guard.add (GuardElt.Guard %d) guards))";
//         SyntaxBuilder sb3 =
//             newsb().addMatchEquation(wildcardSB, newsbf(formatSB3,
//                                                         functionName,
//                                                         ruleNum))
//                    .endMatchExpression()
//                    .endMatchEquationValue()
//                    .endMatchEquation();

//         new VisitKORE() {
//             private SyntaxBuilder sb1;
//             private SyntaxBuilder sb2;
//             private String functionStr;
//             private int arity;

//             @Override
//             public Void apply(KApply k) {
//                 List<K> kitems = k.klist().items();
//                 String klabel = k.klabel().name();

//                 boolean choiceOrMatch = true;

//                 switch(klabel) {
//                 case "#match":
//                     functionStr = "lookup";
//                     sb1 = newsb();
//                     sb2 = newsb();
//                     arity = 2;
//                     break;
//                 case "#setChoice":
//                     functionStr = "set choice";
//                     sb1 = setChoiceSB1;
//                     sb2 = setChoiceSB2;
//                     arity = 2;
//                     break;
//                 case "#mapChoice":
//                     functionStr = "map choice";
//                     sb1 = mapChoiceSB1;
//                     sb2 = mapChoiceSB2;
//                     arity = 2;
//                     break;
//                 default:
//                     choiceOrMatch = false;
//                     break;
//                 }

//                 if(choiceOrMatch) {
//                     // prettyStackTrace();

//                     checkApplyArity(k, arity, functionStr);

//                     K kLabel1 = kitems.get(0);
//                     K kLabel2 = kitems.get(1);

//                     SyntaxBuilder luMatchValue
//                         = oldConvert(ppk, true,  vars, false).apply(kLabel2);
//                     SyntaxBuilder luLevelUp     = sb1;
//                     SyntaxBuilder luPattern
//                         = oldConvert(ppk, false, vars, false).apply(kLabel1);
//                     SyntaxBuilder luWildcardEqn = sb3;
//                     SyntaxBuilder luLevelDown   = sb2;

//                     res.endMatchEquationPattern();
//                     res.beginMatchEquationValue();
//                     res.beginMatchExpression(luMatchValue);
//                     res.append(luLevelUp);
//                     res.beginMatchEquation();
//                     res.beginMatchEquationPattern();
//                     res.append(luPattern);

//                     suffStack.add(luWildcardEqn);
//                     suffStack.add(luLevelDown);
//                 }

//                 return super.apply(k);
//             }
//         }.apply(requires);

//         SyntaxBuilder suffSB = newsb();
//         while(!suffStack.isEmpty()) { suffSB.append(suffStack.pollLast()); }

//         return newSBPair(res, suffSB);
//     }

//     private static String prettyStackTrace() {
//         Pattern funcPat = Pattern.compile("^org[.]kframework.*$");

//         SyntaxBuilder sb = newsb();
//         sb.appendf(";; DBG: ----------------------------%n");
//         sb.appendf(";; DBG: apply executed:%n");
//         StackTraceElement[] traceArray = Thread.currentThread().getStackTrace();
//         List<StackTraceElement> trace = newArrayList(traceArray.length);

//         for(StackTraceElement ste : traceArray) { trace.add(ste); }

//         trace = trace
//             .stream()
//             .filter(x -> funcPat.matcher(x.toString()).matches())
//             .collect(toListC());

//         int skip = 1;
//         for(StackTraceElement ste : trace) {
//             if(skip > 0) {
//                 skip--;
//             } else {
//                 sb.appendf(";; DBG: trace: %20s %6s %30s%n",
//                            ste.getMethodName(),
//                            "(" + Integer.toString(ste.getLineNumber()) + ")",
//                            "(" + ste.getFileName() + ")");
//             }
//         }
//         return sb.toString();
//     }

//     private static SBPair newSBPair(SyntaxBuilder a, SyntaxBuilder b) {
//         return new SBPair(a, b);
//     }

//     private static class SBPair {
//         private final SyntaxBuilder fst, snd;

//         public SBPair(SyntaxBuilder fst, SyntaxBuilder snd) {
//             this.fst = fst;
//             this.snd = snd;
//         }


//         public SyntaxBuilder getFst() {
//             return fst;
//         }

//         public SyntaxBuilder getSnd() {
//             return snd;
//         }
//     }

//     private static SyntaxBuilder oldConvert(SetMultimap<KVariable, String> vars) {
//         SyntaxBuilder sb = newsb();
//         for(Collection<String> nonLinearVars : vars.asMap().values()) {
//             if(nonLinearVars.size() < 2) { continue; }
//             Iterator<String> iter = nonLinearVars.iterator();
//             String last = iter.next();
//             while (iter.hasNext()) {
//                 //handle nonlinear variables in pattern
//                 String next = iter.next();
//                 sb.beginRender();
//                 sb.addApplication("eq", newsb(last), newsb(next));
//                 sb.addSpace();
//                 sb.addKeyword("&&");
//                 sb.addSpace();
//                 sb.endRender();
//                 last = next;
//             }
//         }
//         sb.addValue("true");
//         return sb;
//     }

//     private FuncVisitor oldConvert(PreprocessedKORE ppk,
//                                    boolean rhs,
//                                    SetMultimap<KVariable, String> vars,
//                                    boolean useNativeBooleanExp) {
//         return new FuncVisitor(ppk, rhs, vars, useNativeBooleanExp);
//     }
