// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.definition.Module;
import org.kframework.builtin.Sorts;
import org.kframework.kil.Attribute;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.KLabel;

import java.util.Map;

//import static org.kframework.Collections.*;
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
        return newsb()
            .append(genSortType(ppk))
            .append(genKLabelType(ppk))
            .append(genPrintSortFunc(ppk))
            .append(genPrintKLabelFunc(ppk))
            .append(genParseSortFunc(ppk))
            .append(genParseKLabelFunc(ppk))
            .append(genCollectionForFunc(ppk))
            .append(genUnitForFunc(ppk))
            .append(genElementForFunc(ppk))
            .append(genOtherConstants())
            .append(preludeSB);
    }

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
