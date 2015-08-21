// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.kil.FloatBuiltin;
import org.kframework.kore.KLabel;
import org.kframework.kore.Sort;
import org.kframework.mpfr.BigFloat;
import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.utils.StringUtil.*;

/**
 * Strings and functions specific to the OCaml backend
 *
 * @author Remy Goldschmidt
 */
public final class OCamlIncludes {
    public static final Pattern identifierChar = Pattern.compile("[A-Za-z0-9_]");
    public static final String
        TRUE, FALSE, BOOL,
        INT, FLOAT, STRING,
        SET, SET_CONCAT,
        LIST, LIST_CONCAT;

    static {
        TRUE        = "(Bool true)";
        FALSE       = "(Bool true)";
        BOOL        = encodeStringToIdentifier(Sort("Bool"));
        STRING      = encodeStringToIdentifier(Sort("String"));
        INT         = encodeStringToIdentifier(Sort("Int"));
        FLOAT       = encodeStringToIdentifier(Sort("Float"));
        SET         = encodeStringToIdentifier(Sort("Set"));
        SET_CONCAT  = encodeStringToIdentifier(KLabel("_Set_"));
        LIST        = encodeStringToIdentifier(Sort("List"));
        LIST_CONCAT = encodeStringToIdentifier(KLabel("_List_"));
    }

    public static final SyntaxBuilder wildcardSB, bottomSB, choiceSB, resultSB;

    static {
        wildcardSB = newsbv("_");
        bottomSB = newsbv("[Bottom]");
        choiceSB = newsbv("choice");
        resultSB = newsbv("result");
    }

    private static long counter = 0;

    // public static final SyntaxBuilder definitionsSB = newsb();

    // addModule(SyntaxBuilder moduleName, SyntaxBuilder moduleType, SBPair... bindings)                              | <module name="..." type="...">... <module-binding name="...">type</module-binding> ...</module>
    // addSignature(SyntaxBuilder... specifications)                                                                  | <signature></signature>
    // addConstraints(SyntaxBuilder moduleType, SyntaxBuilder... constraints)                                         |
    // addTypeConstraint(SyntaxBuilder valueTypeConstr, SyntaxBuilder valueTypeEq, SyntaxBuilder... valueTypeParams)  |
    // addFunctor(SyntaxBuilder moduleName, SyntaxBuilder moduleType, SyntaxBuilder moduleType)                       |
    // addModuleValue(SyntaxBuilder valueName, SyntaxBuilder valueType)                                               |
    // begin/endBinaryOpApp()                                                                                         |
    // addBinaryOp(String symbol)                                                                                     |
    //                                                                                                                |


    // static {
    //     definitionsSB
    //         .addModuleSigj
    // }

    public static final String kType =
        "t = kitem list\n" +
        " and kitem = KApply of klabel * t list\n" +
        "           | KToken of sort * string\n" +
        "           | InjectedKLabel of klabel\n" +
        "           | Map of t m\n" +
        "           | List of t list\n" +
        "           | Set of s\n" +
        "           | Int of Z.t\n" +
        "           | String of string\n" +
        "           | Bool of bool\n" +
        "           | Bottom\n";

    public static final SyntaxBuilder preludeSB;
    public static final SyntaxBuilder midludeSB;
    public static final SyntaxBuilder postludeSB;

    static {
        SyntaxBuilder sb = newsb();
        sb.appendf("%s%n", "module type S =");
        sb.appendf("%s%n", "sig");
        sb.appendf("%s%n", "  type 'a m");
        sb.appendf("%s%n", "  type s");
        sb.appendf("%s%n", "  type " + kType);
        sb.appendf("%s%n", "  val compare : t -> t -> int");
        sb.appendf("%s%n", "  val compare_kitem : kitem -> kitem -> int");
        sb.appendf("%s%n", "  val compare_klist : t list -> t list -> int");
        sb.appendf("%s%n", "end ");
        sb.appendf("%s%n", "");
        sb.appendf("%s%n", "");
        sb.appendf("%s%n", "module rec K : (S with type 'a m = 'a Map.Make(K).t and type s = Set.Make(K).t)  = ");
        sb.appendf("%s%n", "struct");
        sb.appendf("%s%n", "  module KMap = Map.Make(K)");
        sb.appendf("%s%n", "  module KSet = Set.Make(K)");
        sb.appendf("%s%n", "  type 'a m = 'a KMap.t");
        sb.appendf("%s%n", "  and s = KSet.t");
        sb.appendf("%s%n", "  and " + kType);
        sb.appendf("%s%n", "  let rec compare c1 c2 = match (c1, c2) with");
        sb.appendf("%s%n", "    | [], [] -> 0");
        sb.appendf("%s%n", "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare_kitem hd1 hd2 in if v = 0 then compare tl1 tl2 else v");
        sb.appendf("%s%n", "    | (hd1 :: tl1), _ -> -1");
        sb.appendf("%s%n", "    | _ -> 1");
        sb.appendf("%s%n", "  and compare_kitem c1 c2 = match (c1, c2) with");
        sb.appendf("%s%n", "    | (KApply(kl1, k1)), (KApply(kl2, k2)) -> let v = compare_klabel kl1 kl2 in if v = 0 then compare_klist k1 k2 else v");
        sb.appendf("%s%n", "    | (KToken(s1, st1)), (KToken(s2, st2)) -> let v = compare_sort s1 s2 in if v = 0 then Pervasives.compare st1 st2 else v");
        sb.appendf("%s%n", "    | (InjectedKLabel kl1), (InjectedKLabel kl2) -> compare_klabel kl1 kl2");
        sb.appendf("%s%n", "    | (Map m1), (Map m2) -> (KMap.compare) compare m1 m2");
        sb.appendf("%s%n", "    | (List l1), (List l2) -> compare_klist l1 l2");
        sb.appendf("%s%n", "    | (Set s1), (Set s2) -> (KSet.compare) s1 s2");
        sb.appendf("%s%n", "    | (Int i1), (Int i2) -> Z.compare i1 i2");
        sb.appendf("%s%n", "    | (String s1), (String s2) -> Pervasives.compare s1 s2");
        sb.appendf("%s%n", "    | (Bool b1), (Bool b2) -> if b1 = b2 then 0 else if b1 then -1 else 1");
        sb.appendf("%s%n", "    | Bottom, Bottom -> 0");
        sb.appendf("%s%n", "    | KApply(_, _), _ -> -1");
        sb.appendf("%s%n", "    | _, KApply(_, _) -> 1");
        sb.appendf("%s%n", "    | KToken(_, _), _ -> -1");
        sb.appendf("%s%n", "    | _, KToken(_, _) -> 1");
        sb.appendf("%s%n", "    | InjectedKLabel(_), _ -> -1");
        sb.appendf("%s%n", "    | _, InjectedKLabel(_) -> 1");
        sb.appendf("%s%n", "    | Map(_), _ -> -1");
        sb.appendf("%s%n", "    | _, Map(_) -> 1");
        sb.appendf("%s%n", "    | List(_), _ -> -1");
        sb.appendf("%s%n", "    | _, List(_) -> 1");
        sb.appendf("%s%n", "    | Set(_), _ -> -1");
        sb.appendf("%s%n", "    | _, Set(_) -> 1");
        sb.appendf("%s%n", "    | Int(_), _ -> -1");
        sb.appendf("%s%n", "    | _, Int(_) -> 1");
        sb.appendf("%s%n", "    | String(_), _ -> -1");
        sb.appendf("%s%n", "    | _, String(_) -> 1");
        sb.appendf("%s%n", "    | Bool(_), _ -> -1");
        sb.appendf("%s%n", "    | _, Bool(_) -> 1");
        sb.appendf("%s%n", "end");
        sb.appendf("%s%n", "");
        sb.appendf("%s%n", "  module KMap = Map.Make(K)");
        sb.appendf("%s%n", "  module KSet = Set.Make(K)");
        sb.appendf("%s%n", "");
        sb.appendf("%s%n", "open K");
        sb.appendf("%s%n", "type k = K.t");
        sb.appendf("%s%n", "exception Stuck of k");
        sb.appendf("%s%n", "module GuardElt = struct");
        sb.appendf("%s%n", "  type t = Guard of int");
        sb.appendf("%s%n", "  let compare c1 c2 = match c1 with Guard(i1) -> match c2 with Guard(i2) -> i2 - i1");
        sb.appendf("%s%n", "end");
        sb.appendf("%s%n", "module Guard = Set.Make(GuardElt)");
        sb.appendf("%s%n", "let freshCounter : Z.t ref = ref Z.zero");
        preludeSB = newsb().append(sb);
    }

    static {
        SyntaxBuilder sb = newsb();
        sb.appendf("%s%n", "let eq k1 k2 = k1 = k2");
        sb.appendf("%s%n", "let isTrue(c: k) : bool = match c with");
        sb.appendf("%s%n", "| ([" + TRUE + "]) -> true");
        sb.appendf("%s%n", "| _ -> false");
        sb.appendf("%s%n", "let rec list_range (c: k list * int * int) : k list = match c with");
        sb.appendf("%s%n", "| (_, 0, 0) -> []");
        sb.appendf("%s%n", "| (head :: tail, 0, len) -> head :: list_range(tail, 0, len - 1)");
        sb.appendf("%s%n", "| (_ :: tail, n, len) -> list_range(tail, n - 1, len)");
        sb.appendf("%s%n", "| ([], _, _) -> raise(Failure \"list_range\")");
        sb.appendf("%s%n", "let rec print_klist(c: k list) : string = match c with");
        sb.appendf("%s%n", "| [] -> \".KList\"");
        sb.appendf("%s%n", "| e::[] -> print_k(e)");
        sb.appendf("%s%n", "| e1::e2::l -> print_k(e1) ^ \", \" ^ print_klist(e2::l)");
        sb.appendf("%s%n", "and print_k(c: k) : string = match c with");
        sb.appendf("%s%n", "| [] -> \".K\"");
        sb.appendf("%s%n", "| e::[] -> print_kitem(e)");
        sb.appendf("%s%n", "| e1::e2::l -> print_kitem(e1) ^ \" ~> \" ^ print_k(e2::l)");
        sb.appendf("%s%n", "and print_kitem(c: kitem) : string = match c with");
        sb.appendf("%s%n", "| KApply(klabel, klist) -> print_klabel(klabel) ^ \"(\" ^ print_klist(klist) ^ \")\"");
        sb.appendf("%s%n", "| KToken(sort, s) -> \"#token(\\\"\" ^ (String.escaped s) ^ \"\\\", \\\"\" ^ print_sort(sort) ^ \"\\\")\"");
        sb.appendf("%s%n", "| InjectedKLabel(klabel) -> \"#klabel(\" ^ print_klabel(klabel) ^ \")\"");
        sb.appendf("%s%n", "| Bool(b) -> print_kitem(KToken(" + BOOL + ", string_of_bool(b)))");
        sb.appendf("%s%n", "| String(s) -> print_kitem(KToken(" + STRING + ", \"\\\"\" ^ (String.escaped s) ^ \"\\\"\"))");
        sb.appendf("%s%n", "| Int(i) -> print_kitem(KToken(" + INT + ", Z.to_string(i)))");
        sb.appendf("%s%n", "| Bottom -> \"`#Bottom`(.KList)\"");
        sb.appendf("%s%n", "| List(l) -> List.fold_left (fun s k -> \"`_List_`(`ListItem`(\" ^ print_k(k) ^ \"),\" ^ s ^ \")\") \"`.List`(.KList)\" l");
        sb.appendf("%s%n", "| Set(s) -> KSet.fold (fun k s -> \"`_Set_`(`SetItem`(\" ^ print_k(k) ^ \"), \" ^ s ^ \")\") s \"`.Set`(.KList)\"");
        sb.appendf("%s%n", "| Map(m) -> KMap.fold (fun k v s -> \"`_Map_`(`_|->_`(\" ^ print_k(k) ^ \", \" ^ print_k(v) ^ \"), \" ^ s ^ \")\") m \"`.Map`(.KList)\"");
        sb.appendf("%s%n", "let print_set f s = print_string (print_kitem(Set s))");
        sb.appendf("%s%n", "let print_map f m = print_string (print_kitem(Map m))");
        midludeSB = sb;
    }

    static {
        SyntaxBuilder sb = newsb();
        sb.appendf("%s%n", "let run c n=");
        sb.appendf("%s%n", "  try let rec go c n = if n = 0 then c else go (step c) (n - 1)");
        sb.appendf("%s%n", "      in go c n");
        sb.appendf("%s%n", "  with Stuck c' -> c'");
        postludeSB = sb;
    }

    public static final ImmutableSet<String> hookNamespaces;
    public static final ImmutableMap<String, Function<String, SyntaxBuilder>> defSortHooks;
    public static final ImmutableMap<String, Function<String, SyntaxBuilder>> userSortHooks;
    public static final ImmutableMap<String, Function<Sort, SyntaxBuilder>> sortVarHooks;
    public static final ImmutableMap<String, Function<Sort, SyntaxBuilder>> predicateRules;

    private OCamlIncludes() {}

    private static SyntaxBuilder toM(SyntaxBuilder... sbs) {
        SyntaxBuilder eqns = newsb();
        for(Pair<SyntaxBuilder, SyntaxBuilder> pair : toPairsA(sbs)) {
            eqns.addMatchEquation(pair.getLeft(), pair.getRight());
        }
        return eqns;
    }

    // UP TO DATE
    static {
        ImmutableSet.Builder<String> bld = ImmutableSet.builder();
        bld.add("BOOL");
        bld.add("FLOAT");
        bld.add("INT");
        bld.add("IO");
        bld.add("K");
        bld.add("KEQUAL");
        bld.add("KREFLECTION");
        bld.add("LIST");
        bld.add("MAP");
        bld.add("MINT");
        bld.add("SET");
        bld.add("STRING");
        hookNamespaces = bld.build();
    }

    // UP TO DATE
    static {
        ImmutableMap.Builder<String, Function<Sort, SyntaxBuilder>> bld;
        bld = ImmutableMap.builder();
        bld.put("BOOL.Bool",     s -> newsbf("Bool _"));
        bld.put("INT.Int",       s -> newsbf("Int _"));
        bld.put("FLOAT.Float",   s -> newsbf("Float _"));
        bld.put("STRING.String", s -> newsbf("String _"));
        bld.put("LIST.List",     s -> newsbf("List (%s,_,_)",
                                             encodeStringToIdentifier(s)));
        bld.put("MAP.Map",       s -> newsbf("Map (%s,_,_)",
                                             encodeStringToIdentifier(s)));
        bld.put("SET.Set",       s -> newsbf("Set (%s,_,_)",
                                             encodeStringToIdentifier(s)));
        sortVarHooks = bld.build();
    }

    // UP TO DATE
    static {
        Function<ImmutableMap.Builder<String, Function<String, SyntaxBuilder>>,
                 BiConsumer<String, Function<String, SyntaxBuilder>>> funGen;

        funGen = b -> (str, f) -> {
            String upper = str.toUpperCase(new Locale("en"));
            b.put(String.format("%s.%s", upper, str), f);
        };

        ImmutableMap.Builder<String, Function<String, SyntaxBuilder>>
            common, build1, build2;
        common = ImmutableMap.builder();
        build1 = ImmutableMap.builder();
        build2 = ImmutableMap.builder();

        BiConsumer<String, Function<String, SyntaxBuilder>>
            putFunC, putFun1, putFun2;
        putFunC = funGen.apply(common);
        putFun1 = funGen.apply(build1);
        putFun2 = funGen.apply(build2);

        Function<String, SyntaxBuilder> renderBool = s -> {
            return newsbApp("Bool", newsbv(s));
        };

        Function<String, SyntaxBuilder> renderFloat = s -> {
            Pair<BigFloat, Integer> f = FloatBuiltin.parseKFloat(s);
            BigFloat left = f.getLeft();
            Integer right = f.getRight();
            int prec = left.precision();
            SyntaxBuilder val = newsbApp("Gmp.FR.from_string_prec_base",
                                         newsbInt(prec),
                                         newsbn("GMP_RNDN"),
                                         newsbInt(10),
                                         newsbStr(left.toString()));
            return newsbApp("round_to_range",
                            newsbApp("Float",
                                     newsbTup(val,
                                              newsbInt(right),
                                              newsbInt(prec))));
        };

        Function<String, SyntaxBuilder> renderString = s -> {
            String unquoted = unquoteKString(unquoteKString("\"" + s + "\""));
            return newsbApp("String", newsbStr(unquoted));
        };

        Function<String, SyntaxBuilder> renderInt = s -> {
            return newsbApp("Int", newsbApp("Z.of_string", newsbStr(s)));
        };

        Function<String, SyntaxBuilder> renderIntLazy = s -> {
            return newsbApp("Int", newsbv(encodeStringToIntConst(s)));
        };

        putFunC.accept("Bool",   renderBool);
        putFunC.accept("Float",  renderFloat);
        putFunC.accept("String", renderString);
        putFun1.accept("Int",    renderInt);
        putFun2.accept("Int",    renderIntLazy);

        build1.putAll(common.build());
        build2.putAll(common.build());

        userSortHooks = build1.build();
        defSortHooks  = build2.build();
    }

    // UP TO DATE
    static {
        ImmutableMap.Builder<String, Function<Sort, SyntaxBuilder>> bld;
        bld = ImmutableMap.builder();
        SyntaxBuilder trueSB = newsbv("[Bool true]");
        bld.put("K.K",           s -> toM(newsbp("k1"),         trueSB));
        bld.put("K.KItem",       s -> toM(newsbp("[k1]"),       trueSB));
        bld.put("INT.Int",       s -> toM(newsbp("[Int _]"),    trueSB));
        bld.put("STRING.String", s -> toM(newsbp("[String _]"), trueSB));
        bld.put("BOOL.Bool",     s -> toM(newsbp("[Bool _]"),   trueSB));
        bld.put("MAP.Map",       s -> toM(newsbf("[Map (s,_,_)] when (s = %s)",
                                                 encodeStringToIdentifier(s)),
                                          trueSB));
        bld.put("SET.Set",       s -> toM(newsbf("[Set (s,_,_)] when (s = %s)",
                                                 encodeStringToIdentifier(s)),
                                          trueSB));
        bld.put("LIST.List",     s -> toM(newsbf("[List (s,_,_)] when (s = %s)",
                                                 encodeStringToIdentifier(s)),
                                          trueSB));
        predicateRules = bld.build();
    }

    public static SyntaxBuilder genImports() {
        return newsb()
            .addImport("Prelude")
            .addImport("Constants")
            .addImport("Prelude.K")
            .addImport("Gmp")
            .addImport("Def");
    }

    public static String encodeStringToIdentifier(KLabel name) {
        return "Lbl" + encodeStringToAlphanumeric(name.name());
    }

    public static String encodeStringToIdentifier(Sort name) {
        return "Sort" + encodeStringToAlphanumeric(name.name());
    }

    public static String encodeStringToFunction(KLabel name) {
        return "eval" + encodeStringToAlphanumeric(name.name());
    }

    public static String encodeStringToVariable(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("var");
        sb.append(encodeStringToAlphanumeric(name));
        sb.append("_");
        sb.append(counter++); // implicit global state >:(
        return sb.toString();
    }

    public static String encodeStringToAlphanumeric(String name) {
        StringBuilder sb = new StringBuilder();
        boolean inIdentifier = true;
        for (int i = 0; i < name.length(); i++) {
            if(identifierChar.matcher(name).region(i, name.length()).lookingAt()) {
                if(!inIdentifier) {
                    inIdentifier = true;
                    sb.append("'");
                }
                sb.append(name.charAt(i));
            } else {
                if(inIdentifier) {
                    inIdentifier = false;
                    sb.append("'");
                }
                sb.append(String.format("%04x", (int) name.charAt(i)));
            }
        }
        return sb.toString();
    }

    public static String encodeStringToIntConst(String integer) {
        StringBuilder sb = new StringBuilder();
        sb.append("int");
        sb.append(encodeStringToAlphanumeric(integer));
        return sb.toString();
    }

    // This code is decidedly sketchy, and should be thoroughly unit tested
    // Unicode support is also desirable (with Camomile maybe?)
    /**
     * Convert a string in Java to a string representing a string value in OCaml
     * @param value The string to convert
     * @return      A string containing the OCaml source of the given string.
     */
    public static String enquoteString(String value) {
        char delimiter = '"';
        final int length = value.length();
        StringBuilder result = new StringBuilder();
        result.append(delimiter);
        for(int offset = 0, codepoint;
            offset < length;
            offset += Character.charCount(codepoint)) {
            codepoint = value.codePointAt(offset);
            if(codepoint > 0xFF) {
                throw unicodeInOCamlStringError();
            } else if(codepoint == delimiter) {
                result.append("\\" + delimiter);
            } else if(codepoint == '\\') {
                result.append("\\\\");
            } else if(codepoint == '\n') {
                result.append("\\n");
            } else if(codepoint == '\t') {
                result.append("\\t");
            } else if(codepoint == '\r') {
                result.append("\\r");
            } else if(codepoint == '\b') {
                result.append("\\b");
            } else if(codepoint >= 32 && codepoint < 127) {
                result.append((char)codepoint);
            } else if(codepoint <= 0xff) {
                result.append("\\");
                result.append(String.format("%03d", codepoint));
            }
        }
        result.append(delimiter);
        return result.toString();
    }


    /**
     * An error resulting from inappropriate use of Unicode in an OCaml string
     */
    private static KEMException unicodeInOCamlStringError() {
        String msg
            = "Unsupported: unicode characters in strings in Ocaml backend.";
        return kemCompilerErrorF(msg);
    }
}
