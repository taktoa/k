// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.definition.NonTerminal;
import org.kframework.builtin.Sorts;
import org.kframework.kil.Attribute;
import org.kframework.kil.ProductionItem;
import org.kframework.kore.K;
import org.kframework.kore.KToken;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.KLabel;
import org.kframework.kore.compile.VisitKORE;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import static org.kframework.Collections.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.backend.func.OCamlIncludes.*;
import static org.kframework.kore.KORE.*;

/**
 * Generate constants in OCaml
 *
 * @author Remy Goldschmidt
 */
public final class FuncConstants {
    private FuncConstants() {}

    public static SyntaxBuilder genConstants(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.append("type sort = \n");
        for(Sort s : ppk.definedSorts) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(s));
            sb.append("\n");
        }
        if(!ppk.definedSorts.contains(Sorts.String())) {
            sb.append("|SortString\n");
        }
        if(!ppk.definedSorts.contains(Sorts.Float())) {
            sb.append("|SortFloat\n");
        }
        sb.append("type klabel = \n");
        for(KLabel label : ppk.definedKLabels) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(label));
            sb.append("\n");
        }
        sb.append("let print_sort(c: sort) : string = match c with \n");
        for(Sort s : ppk.definedSorts) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(s));
            sb.append(" -> ");
            sb.append(enquoteString(s.name()));
            sb.append("\n");
        }
        sb.append("let print_klabel(c: klabel) : string = match c with \n");
        for(KLabel label : ppk.definedKLabels) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(label));
            sb.append(" -> ");
            sb.append(enquoteString(ToKast.apply(label)));
            sb.append("\n");
        }
        sb.append("let parse_sort(c: string) : sort = match c with \n");
        for(Sort s : ppk.definedSorts) {
            sb.append("|");
            sb.append(enquoteString(s.name()));
            sb.append(" -> ");
            sb.append(encodeStringToIdentifier(s));
            sb.append("\n");
        }
        sb.append("| _ -> invalid_arg (\"parse_sort: \" ^ c)\n");
        sb.append("let parse_klabel(c: string) : klabel = match c with \n");
        for(KLabel label : ppk.definedKLabels) {
            sb.append("|");
            sb.append(enquoteString(label.name()));
            sb.append(" -> ");
            sb.append(encodeStringToIdentifier(label));
            sb.append("\n");
        }
        sb.append("| _ -> invalid_arg (\"parse_klabel: \" ^ c)\n");
        sb.append("let collection_for (c: klabel) : klabel = match c with \n");
        for(Map.Entry<KLabel, KLabel> entry : ppk.collectionFor.entrySet()) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(entry.getKey()));
            sb.append(" -> ");
            sb.append(encodeStringToIdentifier(entry.getValue()));
            sb.append("\n");
        }
        sb.append("| _ -> invalid_arg \"collection_for\"\n");
        sb.append("let unit_for (c: klabel) : klabel = match c with \n");
        for(KLabel label : ppk.collectionFor.values().stream().collect(toSetC())) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(label));
            sb.append(" -> ");
            sb.append(encodeStringToIdentifier(KLabel(ppk.attributesFor
                                                         .get(label)
                                                         .<String>get(Attribute.UNIT_KEY)
                                                         .get())));
            sb.append("\n");
        }
        sb.append("| _ -> invalid_arg \"unit_for\"\n");
        sb.append("let el_for (c: klabel) : klabel = match c with \n");
        for(KLabel label : ppk.collectionFor.values().stream().collect(toSetC())) {
            sb.append("|");
            sb.append(encodeStringToIdentifier(label));
            sb.append(" -> ");
            sb.append(encodeStringToIdentifier(KLabel(ppk.attributesFor
                                                         .get(label)
                                                         .<String>get("element")
                                                         .get())));
            sb.append("\n");
        }
        sb.append("| _ -> invalid_arg \"el_for\"\n");
        sb.append("\n\nmodule type S =\n");
        sb.append("sig\n");
        sb.append("  type m\n");
        sb.append("  type s\n");
        sb.append("  type t = kitem list\n");
        Pair<Set<Long>, SyntaxBuilder> pair = printKType(ppk);
        Set<Long> arities = pair.getLeft();
        sb.append(pair.getRight());
        sb.append("  val compare : t -> t -> int\n" +
                "  val compare_kitem : kitem -> kitem -> int\n" +
                "  val compare_klist : t list -> t list -> int\n" +
                "end\n");
        sb.append("module rec K : (S with type m = K.t Map.Make(K).t and type s = Set.Make(K).t)  =\n" +
                "struct\n" +
                "  module KMap = Map.Make(K)\n" +
                "  module KSet = Set.Make(K)\n" +
                "  type m = K.t KMap.t\n" +
                "  and s = KSet.t\n" +
                "  and t = kitem list\n");
        sb.append(printKType(ppk).getRight());
        sb.append("  let rec compare c1 c2 = if c1 == c2 then 0 else match (c1, c2) with\n" +
                "    | [], [] -> 0\n" +
                "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare_kitem hd1 hd2 in if v = 0 then compare tl1 tl2 else v\n" +
                "    | (hd1 :: tl1), _ -> -1\n" +
                "    | _ -> 1\n" +
                "  and compare_kitem c1 c2 = if c1 == c2 then 0 else match (c1, c2) with\n");
        for(long arity : arities) {
            sb.append("    | KApply");
            sb.append(Long.toString(arity));
            sb.append("(lbl1");
            for(int i = 0; i < arity; i++) {
                sb.append(",k");
                sb.append(Integer.toString(i));
                sb.append("_1");
            }
            sb.append("),KApply");
            sb.append(Long.toString(arity));
            sb.append("(lbl2");
            for(int i = 0; i < arity; i++) {
                sb.append(",k");
                sb.append(Integer.toString(i));
                sb.append("_2");
            }
            if(arity > 0) {
                sb.append(") -> (let v = Pervasives.compare lbl1 lbl2 in if v = 0 then ");
                int i;
                for(i = 0; i < arity - 1; i++) {
                    sb.append("(let v = compare k");
                    sb.append(Integer.toString(i));
                    sb.append("_1 k");
                    sb.append(Integer.toString(i));
                    sb.append("_2 in if v = 0 then ");
                }
                sb.append("compare k");
                sb.append(Integer.toString(i));
                sb.append("_1 k");
                sb.append(Integer.toString(i));
                sb.append("_2\n");
                for(i = 0; i < arity; i++) {
                    sb.append(" else v)\n");
                }
            } else {
                sb.append(") -> Pervasives.compare lbl1 lbl2\n");
            }
        }
        sb.append("    | (KToken(s1, st1)), (KToken(s2, st2)) -> let v = Pervasives.compare s1 s2 in if v = 0 then Pervasives.compare st1 st2 else v\n" +
                "    | (InjectedKLabel kl1), (InjectedKLabel kl2) -> Pervasives.compare kl1 kl2\n" +
                "    | (Map (_,k1,m1)), (Map (_,k2,m2)) -> let v = Pervasives.compare k1 k2 in if v = 0 then (KMap.compare) compare m1 m2 else v\n" +
                "    | (List (_,k1,l1)), (List (_,k2,l2)) -> let v = Pervasives.compare k1 k2 in if v = 0 then compare_klist l1 l2 else v\n" +
                "    | (Set (_,k1,s1)), (Set (_,k2,s2)) -> let v = Pervasives.compare k1 k2 in if v = 0 then (KSet.compare) s1 s2 else v\n" +
                "    | (Int i1), (Int i2) -> Z.compare i1 i2\n" +
                "    | (Float (f1,e1,p1)), (Float (f2,e2,p2)) -> let v = e2 - e1 in if v = 0 then let v2 = p2 - p1 in if v2 = 0 then Gmp.FR.compare f1 f2 else v2 else v\n" +
                "    | (String s1), (String s2) -> Pervasives.compare s1 s2\n" +
                "    | (Bool b1), (Bool b2) -> if b1 = b2 then 0 else if b1 then -1 else 1\n" +
                "    | Bottom, Bottom -> 0\n");
        for(long i : arities) {
            sb.append("    | KApply");
            sb.append(Long.toString(i));
            sb.append(" _, _ -> -1\n");
            sb.append("    | _, KApply");
            sb.append(Long.toString(i));
            sb.append(" _ -> 1\n");
        }

        sb.append("    | KToken(_, _), _ -> -1\n" +
                "    | _, KToken(_, _) -> 1\n" +
                "    | InjectedKLabel(_), _ -> -1\n" +
                "    | _, InjectedKLabel(_) -> 1\n" +
                "    | Map(_), _ -> -1\n" +
                "    | _, Map(_) -> 1\n" +
                "    | List(_), _ -> -1\n" +
                "    | _, List(_) -> 1\n" +
                "    | Set(_), _ -> -1\n" +
                "    | _, Set(_) -> 1\n" +
                "    | Int(_), _ -> -1\n" +
                "    | _, Int(_) -> 1\n" +
                "    | Float(_), _ -> -1\n" +
                "    | _, Float(_) -> 1\n" +
                "    | String(_), _ -> -1\n" +
                "    | _, String(_) -> 1\n" +
                "    | Bool(_), _ -> -1\n" +
                "    | _, Bool(_) -> 1\n" +
                "  and compare_klist c1 c2 = match (c1, c2) with\n" +
                "    | [], [] -> 0\n" +
                "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare hd1 hd2 in if v = 0 then compare_klist tl1 tl2 else v\n" +
                "    | (hd1 :: tl1), _ -> -1\n" +
                "    | _ -> 1\n" +
                "end\n");
        sb.append("type normal_kitem = KApply of klabel * K.t list\n");
        sb.append("                  | KItem of K.kitem\n");
        sb.append("open K\n");
        sb.append("let normalize (k: kitem) : normal_kitem = match k with \n");
        for(long arity : arities) {
            sb.append("  | KApply");
            sb.append(Long.toString(arity));
            sb.append("(lbl");
            for(int i = 0; i < arity; i++) {
                sb.append(",k");
                sb.append(Integer.toString(i));
            }
            sb.append(") -> KApply (lbl, [");
            String conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = "; ";
            }
            sb.append("])\n");
        }
        sb.append("| v -> KItem v\n");
        sb.append("let denormalize (k: normal_kitem) : kitem = match k with \n");
        for(long arity : arities) {
            sb.append("  | KApply (lbl, [");
            String conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = "; ";
            }
            sb.append("]) -> KApply");
            sb.append(Long.toString(arity));
            sb.append("(lbl");
            for(int i = 0; i < arity; i++) {
                sb.append(",k");
                sb.append(Integer.toString(i));
            }
            sb.append(")\n");
        }
        sb.append("| KItem v -> v\ntype k = K.t\n");
        for(long arity : arities) {
            sb.append("let denormalize");
            sb.append(Long.toString(arity));
            sb.append(" ");
            sb.append(DefinitionToFunc.printFunctionParams(arity));
            sb.append(" : k list = match c with (");
            String conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = ",";
            }
            sb.append(") -> [");
            conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = "; ";
            }
            sb.append("]\n");

            sb.append("let normalize");
            sb.append(Long.toString(arity));
            sb.append(" (c: k list) = match c with [");
            conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = "; ";
            }
            sb.append("] -> (");
            conn = "";
            for(int i = 0; i < arity; i++) {
                sb.append(conn);
                sb.append("k");
                sb.append(Integer.toString(i));
                conn = ",";
            }
            sb.append(")\n");
        }

        Set<String> integerConstants = new HashSet<>();
        for(Rule r : ppk.rules) {
            integers(r.body(), integerConstants);
            integers(r.requires(), integerConstants);
        }
        for(String i : integerConstants) {
            sb.append("let ");
            sb.append(encodeStringToIntConst(i));
            sb.append(" = lazy (Int (Z.of_string \"");
            sb.append(i);
            sb.append("\"))\n");
        }
        forEachKLabel(ppk, t -> {
                if(t.getRight() == 0) {
                    sb.append("let const");
                    sb.append(encodeStringToAlphanumeric(t.getLeft().name()));
                    sb.append(" = KApply0(");
                    sb.append(encodeStringToIdentifier(t.getLeft()));
                    sb.append(")\n");
                }
            });
        return sb;
    }

    private static void integers(K term, Set<String> accum) {
        new VisitKORE() {
            @Override
            public Void apply(KToken k) {
                if (k.sort().equals(Sorts.Int())) {
                    accum.add(k.s());
                }
                return null;
            }
        }.apply(term);
    }

    private static Pair<Set<Long>, SyntaxBuilder> printKType(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.append("  and kitem = KToken of sort * string\n");
        sb.append("            | InjectedKLabel of klabel\n");
        sb.append("            | Map of sort * klabel * m\n");
        sb.append("            | List of sort * klabel * t list\n");
        sb.append("            | Set of sort * klabel * s\n");
        sb.append("            | Int of Z.t\n");
        sb.append("            | Float of Gmp.FR.t * int * int\n");
        sb.append("            | String of string\n");
        sb.append("            | Bool of bool\n");
        sb.append("            | Bottom\n");
        Set<Long> arities = new HashSet<>();
        forEachKLabel(ppk, t -> arities.add(t.getRight()));
        for(long arity : arities) {
            sb.append("            | KApply");
            sb.append(Long.toString(arity));
            sb.append(" of klabel");
            for(int i = 0; i < arity; i++) {
                sb.append(" * t");
            }
            sb.append("\n");
        }
        return Pair.of(arities, sb);
    }

    private static void forEachKLabel(PreprocessedKORE ppk,
                                      Consumer<Pair<KLabel, Long>> action) {
        for(KLabel kl : ppk.definedKLabels) {
            if(   DefinitionToFunc.isLookupKLabel(kl)
               || kl.name().equals("#KToken")) {
                continue;
            }

            Predicate<Object> isNonTerminal = k -> k instanceof NonTerminal;

            ppk.productionsFor
               .get(kl)
               .stream()
               .map(p -> Pair.of(p.klabel().get(),
                                 stream(p.items()).filter(isNonTerminal)
                                                  .count()))
               .distinct()
               .forEach(action);
        }
    }

    // public static SyntaxBuilder genConstants(PreprocessedKORE ppk) {
    //     return newsb()
    //         .append(genSortType(ppk))
    //         .append(genKLabelType(ppk))
    //         .append(genPrintSortFunc(ppk))
    //         .append(genPrintKLabelFunc(ppk))
    //         .append(genParseSortFunc(ppk))
    //         .append(genParseKLabelFunc(ppk))
    //         .append(genCollectionForFunc(ppk))
    //         .append(genUnitForFunc(ppk))
    //         .append(genElementForFunc(ppk))
    //         .append(genOtherConstants())
    //         .append(preludeSB);
    // }

    private static SyntaxBuilder genMatchFunction(SyntaxBuilder pattern,
                                                  SyntaxBuilder value,
                                                  SyntaxBuilder equations) {
        return newsb()
            .beginLetDeclaration()
            .beginLetDefinitions()
            .beginLetEquation()
            .addLetEquationName(newsb().addPattern(pattern))
            .beginLetEquationValue()
            .beginMatchExpression(value)
            .append(equations)
            .endMatchExpression()
            .endLetEquationValue()
            .endLetEquation()
            .endLetDefinitions()
            .endLetDeclaration();
    }

    private static SyntaxBuilder genSortType(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition("sort");
        for(Sort s : ppk.definedSorts) {
            sb.addConstructor(encodeStringToIdentifier(s));
        }
        if(!ppk.definedSorts.contains(Sorts.String())) {
            sb.addConstructor("SortString");
        }
        if(!ppk.definedSorts.contains(Sorts.Float())) {
            sb.addConstructor("SortFloat");
        }
        sb.endTypeDefinition();
        return sb;
    }

    private static SyntaxBuilder genKLabelType(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition("klabel");
        for(KLabel label : ppk.definedKLabels) {
            sb.addConstructor(encodeStringToIdentifier(label));
        }
        sb.endTypeDefinition();
        return sb;
    }

    private static SyntaxBuilder genPrintSortFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("print_sort (c: sort) : string");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(Sort s : ppk.definedSorts) {
            eqns.addMatchEquation(newsbn(encodeStringToIdentifier(s)),
                                  newsbStr(s.name()));
        }
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genPrintKLabelFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("print_klabel (c: klabel) : string");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.definedKLabels) {
            eqns.addMatchEquation(newsbp(encodeStringToIdentifier(label)),
                                  newsbStr(ToKast.apply(label)));
        }
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genParseSortFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("parse_sort (c: string) : sort");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(Sort s : ppk.definedSorts) {
            eqns.addMatchEquation(newsbStr(s.name()),
                                  newsbn(encodeStringToIdentifier(s)));
        }
        eqns.addMatchEquation(newsbp("_"),
                              newsbv("invalid_arg (\"parse_sort: \" ^ c)"));
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genParseKLabelFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("parse_klabel (c: string) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.definedKLabels) {
            eqns.addMatchEquation(newsbStr(label.name()),
                                  newsbp(encodeStringToIdentifier(label)));
        }
        eqns.addMatchEquation(newsbp("_"),
                              newsbv("invalid_arg (\"parse_klabel: \" ^ c)"));
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genCollectionForFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("collection_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(Map.Entry<KLabel, KLabel> entry : ppk.collectionFor.entrySet()) {
            eqns.addMatchEquation(newsbp(encodeStringToIdentifier(entry.getKey())),
                                  newsbv(encodeStringToIdentifier(entry.getValue())));
        }
        eqns.addMatchEquation(newsbp("_"),
                              newsbv("invalid_arg \"collection_for\""));
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genUnitForFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("unit_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.collectionFor.values()
                                            .stream()
                                            .collect(toSetC())) {
            KLabel val = KLabel(mm.attributesFor()
                                  .apply(label)
                                  .<String>get(Attribute.UNIT_KEY)
                                  .get());
            eqns.addMatchEquation(newsbp(encodeStringToIdentifier(label)),
                                  newsbv(encodeStringToIdentifier(val)));
        }
        eqns.addMatchEquation(newsbp("_"),
                              newsbv("invalid_arg \"unit_for\""));
        return genMatchFunction(name, value, eqns);
    }

    private static SyntaxBuilder genElementForFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("el_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.collectionFor.values()
                                            .stream()
                                            .collect(toSetC())) {
            KLabel val = KLabel(mm.attributesFor()
                                  .apply(label)
                                  .<String>get("element")
                                  .get());
            eqns.addMatchEquation(newsbp(encodeStringToIdentifier(label)),
                                  newsbv(encodeStringToIdentifier(val)));
        }
        eqns.addMatchEquation(newsbp("_"),
                              newsbv("invalid_arg \"el_for\""));
        return genMatchFunction(name, value, eqns);
    }

    public static SyntaxBuilder genOtherConstants() {
        return newsb()
            .addGlobalLet(newsbn("boolSort"),        newsbv(BOOL))
            .addGlobalLet(newsbn("stringSort"),      newsbv(STRING))
            .addGlobalLet(newsbn("intSort"),         newsbv(INT))
            .addGlobalLet(newsbn("floatSort"),       newsbv(FLOAT))
            .addGlobalLet(newsbn("setSort"),         newsbv(SET))
            .addGlobalLet(newsbn("setConcatLabel"),  newsbv(SET_CONCAT))
            .addGlobalLet(newsbn("listSort"),        newsbv(LIST))
            .addGlobalLet(newsbn("listConcatLabel"), newsbv(LIST_CONCAT));
    }
}
