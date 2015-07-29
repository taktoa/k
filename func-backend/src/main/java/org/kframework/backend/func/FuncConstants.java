// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.builtin.Sorts;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.utils.StringUtil;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

//import static org.kframework.kore.KORE.*;
import static org.kframework.backend.func.FuncUtil.*;
import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Generate constants in OCaml
 *
 * @author Remy Goldschmidt
 */
public class FuncConstants {
    public static SyntaxBuilder genConstants(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();

        sb.append(addSortType(ppk));
        sb.append(addSortOrderFunc(ppk));
        sb.append(addKLabelType(ppk));
        sb.append(addKLabelOrderFunc(ppk));
        sb.append(OCamlIncludes.preludeSB);
        sb.append(addPrintSort(ppk));
        sb.append(addPrintKLabel(ppk));
        sb.append(OCamlIncludes.midludeSB);

        return sb;
    }

    private static Function<String, String> wrapPrint(String pfx) {
        return x -> pfx + encodeStringToAlphanumeric(x);
    }

    private static SyntaxBuilder addSimpleFunc(Collection<String> pats,
                                               Collection<String> vals,
                                               String args,
                                               String outType,
                                               String funcName,
                                               String matchVal) {
        List<String> pl = pats.stream().collect(toList());
        List<String> vl = vals.stream().collect(toList());
        return
            newsb()
            .addGlobalLet(newsbf("%s(%s) : %s",
                                 funcName,
                                 args,
                                 outType),
                          newsb().addMatch(newsb().addValue(matchVal),
                                           pl,
                                           vl));

    }

    private static SyntaxBuilder addSimpleFunc(Collection<String> pats,
                                               Collection<String> vals,
                                               String inType,
                                               String outType,
                                               String funcName) {
        String varName = String.valueOf(inType.charAt(0));
        String arg = String.format("%s: %s", varName, inType);
        return addSimpleFunc(pats, vals, arg, outType, funcName, varName);
    }

    private static <T> SyntaxBuilder addOrderFunc(Collection<T> elems,
                                                  Function<T, String> print,
                                                  String pfx,
                                                  String tyName) {
        String fnName = String.format("order_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(print)
                                 .map(wrapPrint(pfx))
                                 .collect(toList());
        List<String> vals = rangeInclusive(pats.size()).stream()
                                                       .map(x -> Integer.toString(x))
                                                       .collect(toList());

        return addSimpleFunc(pats, vals, tyName, "int", fnName);
    }

    private static <T> SyntaxBuilder addPrintFunc(Collection<T> elems,
                                                  Function<T, String> patPrint,
                                                  Function<T, String> valPrint,
                                                  String pfx,
                                                  String tyName) {
        String fnName = String.format("print_%s", tyName);

        List<String> pats = elems.stream()
                                 .map(patPrint)
                                 .map(wrapPrint(pfx))
                                 .collect(toList());

        List<String> vals = elems.stream()
                                 .map(valPrint.andThen(StringUtil::enquoteCString))
                                 .collect(toList());

        return addSimpleFunc(pats, vals, tyName, "string", fnName);
    }

    private static SyntaxBuilder addType(Collection<String> cons,
                                         String tyName) {
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition(tyName);
        for(String c : cons) {
            sb.addConstructor(newsb().addConstructorName(c));
        }
        sb.endTypeDefinition();
        return sb;
    }

    private static <T> SyntaxBuilder addEnumType(Collection<T> toEnum,
                                                 Function<T, String> print,
                                                 String pfx,
                                                 String tyName) {
        List<String> cons = toEnum.stream()
                                  .map(print)
                                  .map(wrapPrint(pfx))
                                  .collect(toList());
        return addType(cons, tyName);
    }


    private static SyntaxBuilder addSortType(PreprocessedKORE ppk) {
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition("sort");
        for(Sort s : ppk.definedSorts) {
            sb.addConstructor(newsb()
                              .addConstructorName(encodeStringToIdentifier(s)));
        }
        if(! ppk.definedSorts.contains(Sorts.String())) {
            sb.addConstructor(newsb()
                              .addConstructorName("SortString"));
        }
        sb.endTypeDefinition();
        return sb;
    }

    private static SyntaxBuilder addKLabelType(PreprocessedKORE ppk) {
        return addEnumType(ppk.definedKLabels,
                           x -> x.name(),
                           "Lbl",
                           "klabel");
    }

    private static SyntaxBuilder addSortOrderFunc(PreprocessedKORE ppk) {
        return addOrderFunc(ppk.definedSorts,
                            x -> x.name(),
                            "Sort",
                            "sort");
    }

    private static SyntaxBuilder addKLabelOrderFunc(PreprocessedKORE ppk) {
        return addOrderFunc(ppk.definedKLabels,
                            x -> x.name(),
                            "Lbl",
                            "klabel");
    }

    private static SyntaxBuilder addPrintSort(PreprocessedKORE ppk) {
        return addPrintFunc(ppk.definedSorts,
                            x -> x.name(),
                            x -> x.name(),
                            "Sort",
                            "sort");
    }

    private static SyntaxBuilder addPrintKLabel(PreprocessedKORE ppk) {
        return addPrintFunc(ppk.definedKLabels,
                            x -> x.name(),
                            x -> ToKast.apply(x),
                            "Lbl",
                            "klabel");
    }
}
