// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.builtin.Sorts;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;

import static org.kframework.backend.func.FuncUtil.*;
// import static org.kframework.backend.func.OCamlIncludes.*;

/**
 * Generate constants in OCaml
 *
 * @author Remy Goldschmidt
 */
public class FuncConstants {
    public static SyntaxBuilder genConstants(PreprocessedKORE ppk) {
        return newsb()
            .append(genSortType(ppk))
            .append(genKLabelType(ppk))
            .append(genPrintSortFunc(ppk))
            .append(genPrintKLabelFunc(ppk))
            .append(genCollectionForFunc(ppk))
            .append(genUnitForFunc(ppk))
            .append(genElementForFunc(ppk))
            .append(genOtherConstants(ppk));
    }

    private SyntaxBuilder genMatchFunction(SyntaxBuilder pattern,
                                           SyntaxBuilder value,
                                           SyntaxBuilder equations) {
        return newsb()
            .beginLetDeclaration()
            .beginLetEquations()
            .beginLetEquation()
            .addLetEquationName(newsbp(pattern))
            .endLetEquationValue()
            .beginMatchExpression(value)
            .append(equations)
            .endMatchExpression()
            .endLetEquationValue()
            .endLetEquation()
            .endLetEquations()
            .endLetDeclaration();
    }

    private SyntaxBuilder genSortType(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition("sort");
        if(fastCompilation) {
            sb.addConstructor("Sort", "string");
        } else {
            for(Sort s : iterable(mm.definedSorts())) {
                sb.addConstructor(encodeStringToIdentifier(s));
            }
            if(!mm.definedSorts().contains(Sorts.String())) {
                sb.addConstructor("SortString");
            }
            if(!mm.definedSorts().contains(Sorts.Float())) {
                sb.addConstructor("SortFloat");
            }
        }
        sb.endTypeDefinition();
        return sb;
    }

    private SyntaxBuilder genKLabelType(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder sb = newsb();
        sb.beginTypeDefinition("klabel");
        if(fastCompilation) {
            sb.addConstructor("KLabel", "string");
        } else {
            for(KLabel label : iterable(mm.definedKLabels())) {
                sb.addConstructor(encodeStringToIdentifier(label));
            }
        }
        sb.endTypeDefinition();
        return sb;
    }

    private SyntaxBuilder genPrintSortFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("print_sort (c: sort) : string");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(Sort s : iterable(mm.definedSorts())) {
            eqns.addMatchEquation(encodeStringToIdentifier(s),
                                  enquoteString(s.name()));
        }
        if(fastCompilation) {
            eqns.addMatchEquation(newsbn("Sort s"),
                                  newsbApp("raise", "Invalid_argument s"));
        }
        return genMatchFunction(name, value, eqns);
    }

    private SyntaxBuilder genPrintKLabelFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("print_klabel (c: klabel) : string");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : iterable(mm.definedKLabels())) {
            eqns.addMatchEquation(encodeStringToIdentifier(label),
                                  enquoteString(ToKast.apply(label)));
        }
        if(fastCompilation) {
            eqns.addMatchEquation(newsbn("KLabel s"),
                                  newsbApp("raise", "Invalid_argument s"));
        }
        return genMatchFunction(name, value, eqns);
    }

    private SyntaxBuilder genCollectionForFunc(PreprocessedKORE ppk) {
        SyntaxBuilder name  = newsb("collection_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(Map.Entry<KLabel, KLabel> entry : ppk.collectionFor.entrySet()) {
            eqns.addMatchEquation(encodeStringToIdentifier(entry.getKey()),
                                  encodeStringToIdentifier(entry.getValue()));
        }
        return genMatchFunction(name, value, eqns);
    }

    private SyntaxBuilder genUnitForFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("unit_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.collectionFor.values()
                                            .stream()
                                            .collect(toSet())) {
            eqns.addMatchEquation(encodeStringToIdentifier(label),
                                  KLabel(mm.attributesFor()
                                           .apply(label)
                                           .<String>get(Attribute.UNIT_KEY)
                                           .get()));
        }
        return genMatchFunction(name, value, eqns);
    }

    private SyntaxBuilder genElementForFunc(PreprocessedKORE ppk) {
        Module mm = ppk.mainModule;
        SyntaxBuilder name  = newsb("el_for (c: klabel) : klabel");
        SyntaxBuilder value = newsbn("c");
        SyntaxBuilder eqns  = newsb();
        for(KLabel label : ppk.collectionFor.values()
                                            .stream()
                                            .collect(toSet())) {
            eqns.addMatchEquation(encodeStringToIdentifier(label),
                                  KLabel(mm.attributesFor()
                                           .apply(label)
                                           .<String>get("element")
                                           .get()));
        }
        return genMatchFunction(name, value, eqns);
    }

    public SyntaxBuilder genOtherConstants() {
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
