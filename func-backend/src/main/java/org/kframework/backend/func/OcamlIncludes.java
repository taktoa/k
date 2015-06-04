package org.kframework.backend.func;

import com.google.common.collect.ImmutableMap;
import java.util.function.Function;
import org.kframework.utils.StringUtil;
import org.kframework.kore.Sort;
import org.kframework.kore.KLabel;
import java.util.regex.Pattern;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;


/**
 * @author: Remy Goldschmidt
 */
public final class OcamlIncludes {
    public static final Pattern identifierChar = Pattern.compile("[A-Za-z0-9_]");
    public static final String TRUE = "(Bool true)";
    public static final String BOOL = encodeStringToIdentifier(Sort("Bool"));
    public static final String STRING = encodeStringToIdentifier(Sort("String"));
    public static final String INT = encodeStringToIdentifier(Sort("Int"));
    private static long counter = 0;

    private static final String kType =
        "t = kitem list\n" +
        " and kitem = KApply of klabel * t list\n" +
        "           | KToken of sort * string\n" +
        "           | InjectedKLabel of klabel\n" +
        "           | Map of t m\n" +
        "           | List of t list\n" +
        "           | Set of s\n" +
        "           | Int of big_int\n" +
        "           | String of string\n" +
        "           | Bool of bool\n" +
        "           | Bottom\n";

    private static final String prelude =
        "open Big_int\n" +
        "module type S =\n" +
        "sig\n" +
        "  type 'a m\n" +
        "  type s\n" +
        "  type " + kType +
        "  val compare : t -> t -> int\n" +
        "end \n" +
        "\n" +
        "\n" +
        "module rec K : (S with type 'a m = 'a Map.Make(K).t and type s = Set.Make(K).t)  = \n" +
        "struct\n" +
        "  module KMap = Map.Make(K)\n" +
        "  module KSet = Set.Make(K)\n" +
        "  type 'a m = 'a KMap.t\n" +
        "  and s = KSet.t\n" +
        "  and " + kType +
        "  let rec compare c1 c2 = match (c1, c2) with\n" +
        "    | [], [] -> 0\n" +
        "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare_kitem hd1 hd2 in if v = 0 then compare tl1 tl2 else v\n" +
        "    | (hd1 :: tl1), _ -> -1\n" +
        "    | _ -> 1\n" +
        "  and compare_kitem c1 c2 = match (c1, c2) with\n" +
        "    | (KApply(kl1, k1)), (KApply(kl2, k2)) -> let v = compare_klabel kl1 kl2 in if v = 0 then compare_klist k1 k2 else v\n" +
        "    | (KToken(s1, st1)), (KToken(s2, st2)) -> let v = compare_sort s1 s2 in if v = 0 then Pervasives.compare st1 st2 else v\n" +
        "    | (InjectedKLabel kl1), (InjectedKLabel kl2) -> compare_klabel kl1 kl2\n" +
        "    | (Map m1), (Map m2) -> (KMap.compare) compare m1 m2\n" +
        "    | (List l1), (List l2) -> compare_klist l1 l2\n" +
        "    | (Set s1), (Set s2) -> (KSet.compare) s1 s2\n" +
        "    | (Int i1), (Int i2) -> compare_big_int i1 i2\n" +
        "    | (String s1), (String s2) -> Pervasives.compare s1 s2\n" +
        "    | (Bool b1), (Bool b2) -> if b1 = b2 then 0 else if b1 then -1 else 1\n" +
        "    | Bottom, Bottom -> 0\n" +
        "    | KApply(_, _), _ -> -1\n" +
        "    | _, KApply(_, _) -> 1\n" +
        "    | KToken(_, _), _ -> -1\n" +
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
        "    | String(_), _ -> -1\n" +
        "    | _, String(_) -> 1\n" +
        "    | Bool(_), _ -> -1\n" +
        "    | _, Bool(_) -> 1\n" +
        "  and compare_klist c1 c2 = match (c1, c2) with\n" +
        "    | [], [] -> 0\n" +
        "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare hd1 hd2 in if v = 0 then compare_klist tl1 tl2 else v\n" +
        "    | (hd1 :: tl1), _ -> -1\n" +
        "    | _ -> 1\n" +
        "  and compare_klabel kl1 kl2 = (order_klabel kl2) - (order_klabel kl1)\n" +
        "  and compare_sort s1 s2 = (order_sort s2) - (order_sort s1)\n" +
        "end\n" +
        "\n" +
        "  module KMap = Map.Make(K)\n" +
        "  module KSet = Set.Make(K)\n" +
        "\n" +
        "open K\n" +
        "type k = K.t" +
        "\n" +
        "exception Stuck of k\n" +
        "module GuardElt = struct\n" +
        "  type t = Guard of int\n" +
        "  let compare c1 c2 = match c1 with Guard(i1) -> match c2 with Guard(i2) -> i2 - i1\n" +
        "end\n" +
        "module Guard = Set.Make(GuardElt)\n";


    private static final String midlude =
        "let eq k1 k2 = k1 = k2\n" +
        "let isTrue(c: k) : bool = match c with\n" +
        "| ([" + TRUE + "]) -> true\n" +
        "| _ -> false\n" +
        "let rec list_range (c: k list * int * int) : k list = match c with\n" +
        "| (l, 0, 0) -> l\n" +
        "| (head :: tail, 0, len) -> head :: list_range(tail, 0, len - 1)\n" +
        "| (_ :: tail, n, len) -> list_range(tail, n - 1, len)\n" +
        "| ([], _, _) -> raise(Failure \"list_range\")\n" +
        "let rec print_klist(c: k list) : string = match c with\n" +
        "| [] -> \".KList\"\n" +
        "| e::[] -> print_k(e)\n" +
        "| e1::e2::l -> print_k(e1) ^ \", \" ^ print_klist(e2::l)\n" +
        "and print_k(c: k) : string = match c with\n" +
        "| [] -> \".K\"\n" +
        "| e::[] -> print_kitem(e)\n" +
        "| e1::e2::l -> print_kitem(e1) ^ \" ~> \" ^ print_k(e2::l)\n" +
        "and print_kitem(c: kitem) : string = match c with\n" +
        "| KApply(klabel, klist) -> print_klabel(klabel) ^ \"(\" ^ print_klist(klist) ^ \")\"\n" +
        "| KToken(sort, s) -> \"#token(\\\"\" ^ s ^ \"\\\", \" ^ print_sort_string(sort) ^ \")\"\n" +
        "| InjectedKLabel(klabel) -> \"#klabel(\" ^ print_klabel(klabel) ^ \")\"\n" +
        "| Bool(b) -> print_kitem(KToken(" + BOOL + ", string_of_bool(b)))\n" +
        "| String(s) -> print_kitem(KToken(" + STRING + ", \"\\\"\" ^ s ^ \"\\\"\"))\n" +
        "| Int(i) -> print_kitem(KToken(" + INT + ", string_of_big_int(i)))\n" +
        "| Bottom -> \"`#Bottom`(.KList)\"\n" +
        "| List(l) -> List.fold_left (fun s k -> \"`_List_`(`ListItem`(\" ^ print_k(k) ^ \"),\" ^ s ^ \")\") \"`.List`(.KList)\" l\n" +
        "| Set(s) -> KSet.fold (fun k s -> \"`_Set_`(`SetItem`(\" ^ print_k(k) ^ \"), \" ^ s ^ \")\") s \"`.Set`(.KList)\"\n" +
        "| Map(m) -> KMap.fold (fun k v s -> \"`_Map_`(`_|->_`(\" ^ print_k(k) ^ \", \" ^ print_k(v) ^ \"), \" ^ s ^ \")\") m \"`.Map`(.KList)\"\n";

    private static final String postlude =
        "let run c n=\n" +
        "  try let rec go c n = if n = 0 then c else go (step c) (n - 1)\n" +
        "      in go c n\n" +
        "  with Stuck c' -> c'\n";

    public static final ImmutableMap<String, String> hooks;
    public static final ImmutableMap<String, Function<String, String>> sortHooks;
    public static final ImmutableMap<String, String> predicateRules;
    
    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("#INT:_%Int_", "[Int a] :: [Int b] :: [] -> [Int (mod_big_int a b)]");
        builder.put("#INT:_+Int_", "[Int a] :: [Int b] :: [] -> [Int (add_big_int a b)]");
        builder.put("#INT:_<=Int_", "[Int a] :: [Int b] :: [] -> [Bool (le_big_int a b)]");
        builder.put("Map:_|->_", "k1 :: k2 :: [] -> [Map (KMap.add k1 k2 KMap.empty)]");
        builder.put("Map:.Map", "[] -> [Map KMap.empty]");
        builder.put("Map:__", "([Map k1]) :: ([Map k2]) :: [] -> [Map (KMap.merge (fun k a b -> match a, b with None, None -> None | None, Some v | Some v, None -> Some v) k1 k2)]");
        builder.put("Map:lookup", "[Map k1] :: k2 :: [] -> (try KMap.find k2 k1 with Not_found -> [Bottom])");
        builder.put("Map:update", "[Map k1] :: k :: v :: [] -> [Map (KMap.add k v k1)]");
        builder.put("Map:remove", "[Map k1] :: k2 :: [] -> [Map (KMap.remove k2 k1)]");
        builder.put("Map:keys", "[Map k1] :: [] -> [Set (KMap.fold (fun key -> KSet.add) k1 KSet.empty)]");
        builder.put("Set:in", "k1 :: [Set k2] :: [] -> [Bool (KSet.mem k1 k2)]");
        builder.put("Set:.Set", "[] -> [Set KSet.empty]");
        builder.put("Set:SetItem", "kl -> [Set (KSet.add (KApply(lbl, kl) :: []) KSet.empty)]");
        builder.put("Set:__", "[Set s1] :: [Set s2] :: [] -> [Set (KSet.union s1 s2)]");
        builder.put("Set:difference", "[Set k1] :: [Set k2] :: [] -> [Set (KSet.diff k1 k2)]");
        builder.put("List:.List", "[] -> [List []]");
        builder.put("List:ListItem", "kl -> [List ([KApply(lbl, kl)] :: [])]");
        builder.put("List:__", "[List l1] :: [List l2] :: [] -> [List (l1 @ l2)]");
        builder.put("List:get", "[List l1] :: [Int i] :: [] -> (try List.nth l1 (int_of_big_int i) with Failure \"nth\" -> [Bottom])");
        builder.put("List:range", "[List l1] :: [Int i1] :: [Int i2] :: [] -> (try [List (list_range (l1, (int_of_big_int i1), (List.length(l1) - (int_of_big_int i2) - (int_of_big_int i1))))] with Failure \"list_range\" -> [Bottom])");
        builder.put("MetaK:#sort", "[KToken (sort, s)] :: [] -> [String (print_sort(sort))] " +
                "| [Int _] :: [] -> [String \"Int\"] " +
                "| [String _] :: [] -> [String \"String\"] " +
                "| [Bool _] :: [] -> [String \"Bool\"] " +
                "| [Map _] :: [] -> [String \"Map\"] " +
                "| [List _] :: [] -> [String \"List\"] " +
                "| [Set _] :: [] -> [String \"Set\"] " +
                "| _ -> [String \"\"]");
        builder.put("#K-EQUAL:_==K_", "k1 :: k2 :: [] -> [Bool (eq k1 k2)]");
        builder.put("#BOOL:_andBool_", "[Bool b1] :: [Bool b2] :: [] -> [Bool (b1 && b2)]");
        builder.put("#BOOL:notBool_", "[Bool b1] :: [] -> [Bool (not b1)]");
        hooks = builder.build();
    }

    static {
        ImmutableMap.Builder<String, Function<String, String>> builder = ImmutableMap.builder();
        builder.put("#BOOL", s -> "(Bool " + s + ")");
        builder.put("#INT", s -> "(Int (big_int_of_string \"" + s + "\"))");
        builder.put("#STRING", s -> "(String " + StringUtil.enquoteCString(StringUtil.unquoteKString(s)) + ")");
        sortHooks = builder.build();
    }

    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("isK", "k1 :: [] -> [Bool true]");
        builder.put("isKItem", "[k1] :: [] -> [Bool true]");
        builder.put("isInt", "[Int _] :: [] -> [Bool true]");
        builder.put("isString", "[String _] :: [] -> [Bool true]");
        builder.put("isBool", "[Bool _] :: [] -> [Bool true]");
        builder.put("isMap", "[Map _] :: [] -> [Bool true]");
        builder.put("isSet", "[Set _] :: [] -> [Bool true]");
        builder.put("isList", "[List _] :: [] -> [Bool true]");
        predicateRules = builder.build();
    }

    private OcamlIncludes() {}

    public static void addPrelude(StringBuilder sb) {
        sb.append(prelude);
    }

    public static void addMidlude(StringBuilder sb) {
        sb.append(midlude);
    }

    public static void addPostlude(StringBuilder sb) {
        sb.append(postlude);
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
        sb.append(counter++);
        return sb.toString();
    }

    private static String encodeStringToAlphanumeric(String name) {
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
}
