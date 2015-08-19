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
    public static final ImmutableMap<String, SyntaxBuilder> hooks;
    public static final ImmutableMap<String, Function<String, SyntaxBuilder>> sortHooks;
    public static final ImmutableMap<String, Function<Sort, SyntaxBuilder>> sortVarHooks;
    public static final ImmutableMap<String, SyntaxBuilder> predicateRules;

    private OCamlIncludes() {}

    static {
        ImmutableMap.Builder<String, SyntaxBuilder> bld;
        bld = ImmutableMap.builder();

        putb(bld, "Map:_|->_",               toSBs("k1 :: k2 :: []",                              "[Map (KMap.singleton k1 k2)]"));
        putb(bld, "Map:.Map",                toSBs("[]",                                          "[Map KMap.empty]"));
        putb(bld, "Map:__",                  toSBs("([Map k1]) :: ([Map k2]) :: []",              "[Map (KMap.merge (fun k a b -> match a, b with None, None -> None | None, Some v | Some v, None -> Some v | Some v1, Some v2 when v1 = v2 -> Some v1) k1 k2)]"));
        putb(bld, "Map:lookup",              toSBs("[Map k1] :: k2 :: []",                        "(try KMap.find k2 k1 with Not_found -> [Bottom])"));
        putb(bld, "Map:update",              toSBs("[Map k1] :: k :: v :: []",                    "[Map (KMap.add k v k1)]"));
        putb(bld, "Map:remove",              toSBs("[Map k1] :: k2 :: []",                        "[Map (KMap.remove k2 k1)]"));
        putb(bld, "Map:keys",                toSBs("[Map k1] :: []",                              "[Set (KMap.fold (fun k v -> KSet.add k) k1 KSet.empty)]"));
        putb(bld, "Map:values",              toSBs("[Map k1] :: []",                              "[Set (KMap.fold (fun key -> KSet.add) k1 KSet.empty)]"));
        putb(bld, "Map:choice",              toSBs("[Map k1] :: []",                              "match KMap.choose k1 with (k, _) -> k"));
        putb(bld, "Map:updateAll",           toSBs("([Map k1]) :: ([Map k2]) :: []",              "[Map (KMap.merge (fun k a b -> match a, b with None, None -> None | None, Some v | Some v, None | Some _, Some v -> Some v) k1 k2)]"));
        putb(bld, "Set:in",                  toSBs("k1 :: [Set k2] :: []",                        "[Bool (KSet.mem k1 k2)]"));
        putb(bld, "Set:.Set",                toSBs("[]",                                          "[Set KSet.empty]"));
        putb(bld, "Set:SetItem",             toSBs("k :: []",                                     "[Set (KSet.singleton k)]"));
        putb(bld, "Set:__",                  toSBs("[Set s1] :: [Set s2] :: []",                  "[Set (KSet.union s1 s2)]"));
        putb(bld, "Set:difference",          toSBs("[Set k1] :: [Set k2] :: []",                  "[Set (KSet.diff k1 k2)]"));
        putb(bld, "Set:inclusion",           toSBs("[Set k1] :: [Set k2] :: []",                  "[Bool (KSet.subset k1 k2)]"));
        putb(bld, "Set:intersection",        toSBs("[Set k1] :: [Set k2] :: []",                  "[Set (KSet.inter k1 k2)]"));
        putb(bld, "Set:choice",              toSBs("[Set k1] :: []",                              "KSet.choose k1"));
        putb(bld, "List:.List",              toSBs("[]",                                          "[List []]"));
        putb(bld, "List:ListItem",           toSBs("k :: []",                                     "[List [k]]"));
        putb(bld, "List:__",                 toSBs("[List l1] :: [List l2] :: []",                "[List (l1 @ l2)]"));
        putb(bld, "List:in",                 toSBs("k1 :: [List k2] :: []",                       "[Bool (List.mem k1 k2)]"));
        putb(bld, "List:get",                toSBs("[List l1] :: [Int i] :: []",                  "let i = Z.to_int i in (try if i >= 0 then List.nth l1 i else List.nth l1 ((List.length l1) + i) with Failure \"nth\" -> [Bottom])"));
        putb(bld, "List:range",              toSBs("[List l1] :: [Int i1] :: [Int i2] :: []",     "(try [List (list_range (l1, (Z.to_int i1), (List.length(l1) - (Z.to_int i2) - (Z.to_int i1))))] with Failure \"list_range\" -> [Bottom])"));
        putb(bld, "#K-EQUAL:_==K_",          toSBs("k1 :: k2 :: []",                              "[Bool (eq k1 k2)]"));
        putb(bld, "#BOOL:_andBool_",         toSBs("[Bool b1] :: [Bool b2] :: []",                "[Bool (b1 && b2)]"));
        putb(bld, "#BOOL:_andThenBool_",     toSBs("[Bool b1] :: [Bool b2] :: []",                "[Bool (b1 && b2)]"));
        putb(bld, "#BOOL:_orBool_",          toSBs("[Bool b1] :: [Bool b2] :: []",                "[Bool (b1 || b2)]"));
        putb(bld, "#BOOL:_orElseBool_",      toSBs("[Bool b1] :: [Bool b2] :: []",                "[Bool (b1 || b2)]"));
        putb(bld, "#BOOL:notBool_",          toSBs("[Bool b1] :: []",                             "[Bool (not b1)]"));
        putb(bld, "#STRING:_+String_",       toSBs("[String s1] :: [String s2] :: []",            "[String (s1 ^ s2)]"));
        putb(bld, "#STRING:_<String_",       toSBs("[String s1] :: [String s2] :: []",            "[Bool ((String.compare s1 s2) < 0)]"));
        putb(bld, "#STRING:_<=String_",      toSBs("[String s1] :: [String s2] :: []",            "[Bool ((String.compare s1 s2) <= 0)]"));
        putb(bld, "#STRING:_>String_",       toSBs("[String s1] :: [String s2] :: []",            "[Bool ((String.compare s1 s2) > 0)]"));
        putb(bld, "#STRING:_>=String_",      toSBs("[String s1] :: [String s2] :: []",            "[Bool ((String.compare s1 s2) >= 0)]"));
        putb(bld, "#STRING:chrChar",         toSBs("[Int i] :: []",                               "[String (String.make 1 (Char.chr (Z.to_int i)))]"));
        putb(bld, "#STRING:findString",      toSBs("[String s1] :: [String s2] :: [Int i] :: []", "try [Int (Z.of_int (Str.search_forward (Str.regexp_string s2) s1 (Z.to_int i)))] with Not_found -> [Int (Z.of_int (-1))]"));
        putb(bld, "#STRING:rfindString",     toSBs("[String s1] :: [String s2] :: [Int i] :: []", "try [Int (Z.of_int (Str.search_backward (Str.regexp_string s2) s1 (Z.to_int i)))] with Not_found -> [Int (Z.of_int (-1))]"));
        putb(bld, "#STRING:lengthString",    toSBs("[String s] :: []",                            "[Int (Z.of_int (String.length s))]"));
        putb(bld, "#STRING:substrString",    toSBs("[String s] :: [Int i1] :: [Int i2] :: []",    "[String (String.sub s (Z.to_int i1) (Z.to_int (Z.add i1 i2)))]"));
        putb(bld, "#STRING:ordChar",         toSBs("[String s] :: []",                            "[Int (Z.of_int (Char.code (String.get s 0)))]"));
        putb(bld, "#INT:_%Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.rem a b)]"));
        putb(bld, "#INT:_+Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.add a b)]"));
        putb(bld, "#INT:_<=Int_",            toSBs("[Int a] :: [Int b] :: []",                    "[Bool (Z.leq a b)]"));
        putb(bld, "#INT:_&Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.logand a b)]"));
        putb(bld, "#INT:_*Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.mul a b)]"));
        putb(bld, "#INT:_-Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.sub a b)]"));
        putb(bld, "#INT:_/Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.div a b)]"));
        putb(bld, "#INT:_<<Int_",            toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.shift_left a (Z.to_int b))]"));
        putb(bld, "#INT:_<Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Bool (Z.lt a b)]"));
        putb(bld, "#INT:_>=Int_",            toSBs("[Int a] :: [Int b] :: []",                    "[Bool (Z.geq a b)]"));
        putb(bld, "#INT:_>>Int_",            toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.shift_right a (Z.to_int b))]"));
        putb(bld, "#INT:_>Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Bool (Z.gt a b)]"));
        putb(bld, "#INT:_^Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.pow a (Z.to_int b))]"));
        putb(bld, "#INT:_xorInt_",           toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.logxor a b)]"));
        putb(bld, "#INT:_|Int_",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.logor a b)]"));
        putb(bld, "#INT:absInt",             toSBs("[Int a] :: []",                               "[Int (Z.abs a)]"));
        putb(bld, "#INT:maxInt",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.max a b)]"));
        putb(bld, "#INT:minInt",             toSBs("[Int a] :: [Int b] :: []",                    "[Int (Z.min a b)]"));
        putb(bld, "#CONVERSION:int2string",  toSBs("[Int i] :: []",                               "[String (Z.to_string i)]"));
        putb(bld, "#CONVERSION:string2int",  toSBs("[String s] :: []",                            "[Int (Z.of_string s)]"));
        putb(bld, "#CONVERSION:string2base", toSBs("[String s] :: [Int i] :: []",                 "[Int (Z.of_string_base (Z.to_int i) s)]"));
        putb(bld, "#FRESH:fresh",            toSBs("[String sort] :: []",                         "let res = freshFunction sort !freshCounter in freshCounter := Z.add !freshCounter Z.one; res"));
        putb(bld, "Collection:size",         toSBs("[List l] :: []",                              "[Int (Z.of_int (List.length l))]",
                                                   "[Map m] :: []",                               "[Int (Z.of_int (KMap.cardinal m))]",
                                                   "[Set s] :: []",                               "[Int (Z.of_int (KSet.cardinal s))]"));
        putb(bld, "MetaK:#sort",             toSBs("[KToken (sort, s)] :: []",                    "[String (print_sort(sort))] ",
                                                   "[Int _] :: []",                               "[String \"Int\"] ",
                                                   "[String _] :: []",                            "[String \"String\"] ",
                                                   "[Bool _] :: []",                              "[String \"Bool\"] ",
                                                   "[Map _] :: []",                               "[String \"Map\"] ",
                                                   "[List _] :: []",                              "[String \"List\"] ",
                                                   "[Set _] :: []",                               "[String \"Set\"] ",
                                                   "_",                                           "[String \"\"]"));

        hooks = bld.build();
    }

    private static SyntaxBuilder[] toSBs(String... strings) {
        SyntaxBuilder[] res = new SyntaxBuilder[strings.length];
        for(int i = 0; i < res.length; i++) {
            res[i] = newsbv(strings[i]);
        }
        return res;
    }

    private static void putb(ImmutableMap.Builder<String, SyntaxBuilder> builder,
                             String hook,
                             SyntaxBuilder... sbs) {
        if(sbs.length % 2 != 0) {
            throw new ExceptionInInitializerError("OCamlIncludes.putme was passed an odd number of arguments");
        }
        SyntaxBuilder eqns = newsb();
        for(int i = 0; (i + 1) < sbs.length; i += 2) {
            eqns.addMatchEquation(sbs[i], sbs[i + 1]);
        }
        builder.put(hook, eqns);
    }

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

    static {
        ImmutableMap.Builder<String, Function<Sort, SyntaxBuilder>> bld;
        bld = ImmutableMap.builder();
        bld.put("BOOL.Bool",     s -> newsb("Bool _"));
        bld.put("INT.Int",       s -> newsb("Int _"));
        bld.put("FLOAT.Float",   s -> newsb("Float _"));
        bld.put("STRING.String", s -> newsb("String _"));
        bld.put("LIST.List",     s -> newsb("List (" + encodeStringToIdentifier(s) + ",_,_)"));
        bld.put("MAP.Map",       s -> newsb("Map (" + encodeStringToIdentifier(s) + ",_,_)"));
        bld.put("SET.Set",       s -> newsb("Set (" + encodeStringToIdentifier(s) + ",_,_)"));
        sortVarHooks = bld.build();
    }

    static {
        final ImmutableMap.Builder<String, Function<String, SyntaxBuilder>> bld;
        bld = ImmutableMap.builder();

        BiConsumer<String, Function<String, SyntaxBuilder>> fun = (str, f) -> {
            String upper = str.toUpperCase(new Locale("en"));
            bld.put(String.format("%s.%s", upper, str), f);
        };

        Function<String, SyntaxBuilder> renderBool = s -> {
            return newsbApp("Bool", newsbv(s));
        };

        Function<String, SyntaxBuilder> renderInt = s -> {
            return newsbApp("Int", newsbApp("Z.of_string", newsbStr(s)));
        };

        Function<String, SyntaxBuilder> renderFloat = s -> {
            Pair<BigFloat, Integer> f = FloatBuiltin.parseKFloat(s);
            BigFloat left = f.getLeft();
            Integer right = f.getRight();
            int prec = left.precision();
            SyntaxBuilder val = newsbApp("FR.from_string_prec_base",
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

        fun.accept("Bool",   renderBool);
        fun.accept("Float",  renderFloat);
        fun.accept("Int",    renderInt);
        fun.accept("String", renderString);
        sortHooks = bld.build();
    }

    static {
        ImmutableMap.Builder<String, SyntaxBuilder> bld;
        bld = ImmutableMap.builder();
        putb(bld, "isK",      toSBs("k1 :: []",         "[Bool true]"));
        putb(bld, "isKItem",  toSBs("[k1] :: []",       "[Bool true]"));
        putb(bld, "isInt",    toSBs("[Int _] :: []",    "[Bool true]"));
        putb(bld, "isString", toSBs("[String _] :: []", "[Bool true]"));
        putb(bld, "isBool",   toSBs("[Bool _] :: []",   "[Bool true]"));
        putb(bld, "isMap",    toSBs("[Map _] :: []",    "[Bool true]"));
        putb(bld, "isSet",    toSBs("[Set _] :: []",    "[Bool true]"));
        putb(bld, "isList",   toSBs("[List _] :: []",   "[Bool true]"));
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

    public static String encodeStringToFunction(String name) {
        return "eval" + encodeStringToAlphanumeric(name);
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
