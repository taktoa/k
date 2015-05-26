// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import com.google.common.collect.Lists;
import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.compile.ConfigurationInfo.Multiplicity;
import org.kframework.definition.Constructors;
import org.kframework.definition.Module;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kore.Assoc;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;
import scala.Option;
import scala.Tuple2;
import scala.Tuple3;
import scala.collection.immutable.Set;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Created by dwightguth on 3/27/15.
 */
public class GenerateSentencesFromConfigDecl {

    /**
     * Takes a configuration declaration and returns the sentences that it desugars into.
     *
     * Cells of multiplicity 1 desugar into an initializer production, an initializer rule, and a cell production.
     * Cells of multiplicity * desugar into an initializer production, an initializer rule, a cell production, and a bag
     * sort to represent a bag of those cells.
     * Cells of multiplicity ? desugar into an initializer production, an initializer rule, a cell production, and an
     * empty production indicating the absence of that cell.
     *
     * Currently the implementation does not handle initializer rules; we will address this eventually.
     * @param body The body of the configuration declaration.
     * @param ensures The ensures clause of the configuration declaration.
     * @param att The attributes of the configuration declaration.
     * @param m The module the configuration declaration exists in.
     * @return A set of sentences representing the configuration declaration.
     */
    public static Set<Sentence> gen(K body, K ensures, Att att, Module m) {
        return genInternal(body, ensures, att, m)._1();
    }

    /**
     * Recurses over a cell and computes all the sentences corresponding to its children, and then generates
     * the sentences for itself.
     * @param term The term to be processed. Can be either a cell, in which case it processes that cell,
     *             a list of cells, in which case it processes each of those cells, or a noncell, in which case
     *             its parent is treated as a leaf cell.
     * @param ensures The ensures clause from the configuration declaration. This is appended to the initializer of
     *                the top cell, but not any other cells. The algorithm assumes this will be null if and only if
     *                it is not the top cell of a configuration declaration.
     * @param cfgAtt The attributes of the configuration declaration. Appended to all cell productions generated.
     * @param m The module the configuration declaration is in. Used to get the sort of leaf cells.
     * @return A tuple of the sentences generated, a list of the sorts of the children of the cell, and the body of the initializer.
     */
    private static Tuple3<Set<Sentence>, List<Sort>, K> genInternal(K term, K ensures, Att cfgAtt, Module m) {
        if (term instanceof KApply) {
            KApply kapp = (KApply) term;
            if (kapp.klabel().name().equals("#configCell")) {
                // is a single cell
                if (kapp.klist().size() == 4) {
                    K startLabel = kapp.klist().items().get(0);
                    K endLabel = kapp.klist().items().get(3);
                    if (startLabel.equals(endLabel)) {
                        if (startLabel instanceof KToken) {
                            KToken label = (KToken) startLabel;
                            if (label.sort().equals(Sort("#CellName"))) {
                                String cellName = label.s();
                                Att cellProperties = getCellPropertiesAsAtt(kapp.klist().items().get(1), cellName, ensures);
                                Multiplicity multiplicity = convertStringMultiplicity(
                                        cellProperties.<String>get("multiplicity"), term);

                                K cellContents = kapp.klist().items().get(2);
                                Tuple3<Set<Sentence>, List<Sort>, K> childResult = genInternal(
                                        cellContents, null, cfgAtt, m);

                                Tuple3<Set<Sentence>, Sort, K> myResult = computeSentencesOfWellFormedCell(multiplicity, cfgAtt, m, cellName, cellProperties,
                                        childResult._2(), childResult._3(), ensures, hasConfigVariable(cellContents));
                                return Tuple3.apply((Set<Sentence>)childResult._1().$bar(myResult._1()), Lists.newArrayList(myResult._2()), myResult._3());
                            }
                        }
                    }
                }
                throw KEMException.compilerError("Malformed cell in configuration declaration.", term);
            } else if (kapp.klabel().name().equals("#cells")) {
                //is a cell bag, and thus represents the multiple children of its parent cell
                if (ensures != null) {
                    //top level cell, therefore, should be the children of the generatedTop cell
                    KToken cellLabel = KToken("generatedTop", Sort("#CellName"));
                    K generatedTop = KApply(KLabel("#configCell"), cellLabel, KApply(KLabel("#cellPropertyListTerminator")), term, cellLabel);
                    return genInternal(generatedTop, ensures, cfgAtt, m);
                }
                List<K> cells = Assoc.flatten(kapp.klabel(), kapp.klist().items(), m);
                Set<Sentence> accumSentences = Set();
                List<Sort> sorts = Lists.newArrayList();
                List<K> initializers = Lists.newArrayList();
                for (K cell : cells) {
                    //for each cell, generate the child and inform the parent of the children it contains
                    Tuple3<Set<Sentence>, List<Sort>, K> childResult = genInternal(cell, null, cfgAtt, m);
                    accumSentences = (Set<Sentence>)accumSentences.$bar(childResult._1());
                    sorts.addAll(childResult._2());
                    initializers.add(childResult._3());
                }
                return Tuple3.apply(accumSentences, sorts, KApply(KLabel("#cells"), immutable(initializers)));
            }
            //TODO: call generic getSort method of some kind
            // child of a leaf cell. Generate no productions, but inform parent that it has a child of a particular sort.
            // A leaf cell initializes to the value specified in the configuration declaration.
            return Tuple3.apply(Set(), Lists.newArrayList(m.sortFor().get(kapp.klabel()).get()), getLeafInitializer(term));
        } else if (term instanceof KToken) {
            // child of a leaf cell. Generate no productions, but inform parent that it has a child of a particular sort.
            // A leaf cell initializes to the value specified in the configuration declaration.
            KToken ktoken = (KToken) term;
            return Tuple3.apply(Set(), Lists.newArrayList(ktoken.sort()), getLeafInitializer(term));
        } else if (term instanceof KSequence || term instanceof KVariable || term instanceof InjectedKLabel) {
            // child of a leaf cell. Generate no productions, but inform parent that it has a child of a particular sort.
            // A leaf cell initializes to the value specified in the configuration declaration.
            return Tuple3.apply(Set(), Lists.newArrayList(Sorts.K()), getLeafInitializer(term));
        } else {
            throw KEMException.compilerError("Unexpected value found in configuration declaration, expected KToken, KSequence, or KApply", term);
        }
    }

    /**
     * Returns true if the specified term has a configuration variable
     * @param contents
     */
    private static boolean hasConfigVariable(K contents) {
        FindConfigVar visitor = new FindConfigVar();
        visitor.apply(contents);
        return visitor.hasConfigVar;
    }

    private static class FindConfigVar extends VisitKORE {
        boolean hasConfigVar;
        @Override
        public Void apply(KToken k) {
            if (k.sort().equals(Sorts.KConfigVar())) {
                hasConfigVar = true;
            }
            return null;
        }
    }

    /**
     * Returns the body of an initializer for a leaf cell: replaces any configuration variables
     * with map lookups in the initialization map.
     * @param leafContents
     * @return
     */
    private static K getLeafInitializer(K leafContents) {
        return new TransformKORE() {
            @Override
            public K apply(KToken k) {
                if (k.sort().equals(Sorts.KConfigVar())) {
                    return KApply(KLabel("Map:lookup"), KVariable("Init"), k);
                }
                return k;
            }
        }.apply(leafContents);
    }

    /**
     * Generates the sentences associated with a particular cell.
     * @param multiplicity The multiplicity of the cell
     * @param configAtt The attributes on the configuration declaration.
     * @param m The module containing the configuration.
     * @param cellName The name of the cell being generated.
     * @param cellProperties The attributes on the configuration cell (&lt;cell foo="bar"&gt;&lt;/cell&gt;
     * @param childSorts The list of sorts computed via recursion of the children of the current cell.
     * @param childInitializer The contents of the cell being processed, converted into the right hand side of an initializer.
     * @param ensures The ensures clause to be used; null if the cell is not a top cell.
     * @param hasConfigurationVariable true if the initializer for the cell requires a configuration variable.
     *                                 This causes cells of multiplicity * or ? to be initialized to a non-empty bag,
     *                                 and the initializer to take a Map argument containing the values of the configuration
     *                                 variables.
     * @return A tuple containing the sentences associated with the cell, the sort of the cell, and the term to be used to initialize
     * this cell in the initializer of its parent cell.
     */
    private static Tuple3<Set<Sentence>, Sort, K> computeSentencesOfWellFormedCell(
            Multiplicity multiplicity,
            Att configAtt,
            Module m,
            String cellName,
            Att cellProperties,
            List<Sort> childSorts,
            K childInitializer,
            K ensures,
            boolean hasConfigurationVariable) {
        String sortName = getSortOfCell(cellName);
        Sort sort = Sort(sortName);

        List<ProductionItem> items = Stream.concat(Stream.concat(Stream.of(
                Terminal("<" + cellName + ">")), childSorts.stream()
                .map(Constructors::NonTerminal)), Stream.of(Terminal("</" + cellName + ">")))
                .collect(Collectors.toList());

        // syntax Cell ::= "initCell" [initializer, function]
        // -or-
        // syntax Cell ::= initCell(Map) [initializer, function]
        // syntax Cell ::= "<cell>" Children... "</cell>" [cell, cellProperties, configDeclAttributes]
        String initLabel = "init" + sort.name();
        Sentence initializer;
        Rule initializerRule;
        if (hasConfigurationVariable) {
            initializer = Production(initLabel, sort, Seq(Terminal(initLabel), Terminal("("), NonTerminal(Sort("Map")), Terminal(")")), Att().add("initializer").add("function"));
            initializerRule = Rule(KRewrite(KApply(KLabel(initLabel), KVariable("Init")), IncompleteCellUtils.make(KLabel("<" + cellName + ">"), false, childInitializer, false)), BooleanUtils.TRUE, ensures == null ? BooleanUtils.TRUE : ensures, Att());
        } else {
            initializer = Production(initLabel, sort, Seq(Terminal(initLabel)), Att().add("initializer").add("function"));
            initializerRule = Rule(KRewrite(KApply(KLabel(initLabel)), IncompleteCellUtils.make(KLabel("<" + cellName + ">"), false, childInitializer, false)), BooleanUtils.TRUE, ensures == null ? BooleanUtils.TRUE : ensures, Att());
        }
        Production cellProduction = Production("<" + cellName + ">", sort, items.stream().collect(Collections.toList()),
                cellProperties.addAll(configAtt));

        if (multiplicity == Multiplicity.STAR) {
            // syntax CellBag ::= Cell
            // syntax CellBag ::= ".CellBag"
            // syntax CellBag  ::= CellBag CellBag [assoc, unit(.CellBag)]
            Sort bagSort = Sort(sortName + "Bag");
            Sentence bagSubsort = Production(bagSort, Seq(NonTerminal(sort)), Att());
            Sentence bagUnit = Production("." + bagSort.name(), bagSort, Seq(Terminal("." + bagSort.name())));
            Sentence bag = Production("_" + bagSort + "_", bagSort, Seq(NonTerminal(bagSort), NonTerminal(bagSort)),
                    Att().add(Attribute.ASSOCIATIVE_KEY, "").add(Attribute.COMMUTATIVE_KEY, "").add(Attribute.UNIT_KEY, "." + bagSort.name()));
            // rule initCell => .CellBag
            // -or-
            // rule initCell(Init) => <cell> Context[$var] </cell>
            K rhs = optionalCellInitializer(hasConfigurationVariable, initLabel);
            return Tuple3.apply(Set(initializer, initializerRule, cellProduction, bagSubsort, bagUnit, bag), bagSort, rhs);
        } else if (multiplicity == Multiplicity.OPTIONAL) {
            // syntax Cell ::= ".Cell"
            Production cellUnit = Production("." + sortName, sort, Seq(Terminal("." + sortName)));
            cellProduction = Production(cellProduction.sort(), cellProduction.items(), cellProduction.att().add(Attribute.UNIT_KEY, cellUnit.klabel().get().name()));
            // rule initCell => .CellBag
            // -or-
            // rule initCell(Init) => <cell> Context[$var] </cell>
            K rhs = optionalCellInitializer(hasConfigurationVariable, initLabel);
            return Tuple3.apply(Set(initializer, initializerRule, cellProduction, cellUnit), sort, rhs);
        } else {
            // rule initCell => <cell> initChildren... </cell>
            // -or-
            // rule initCell(Init) => <cell> initChildren(Init)... </cell>
            K rhs;
            if (hasConfigurationVariable) {
                rhs = KApply(KLabel(initLabel), KVariable("Init"));
            } else {
                rhs = KApply(KLabel(initLabel));
            }
            return Tuple3.apply(Set(initializer, initializerRule, cellProduction), sort, rhs);
        }
    }

    /**
     * Returns the term used to initialize an optinoal cell. An optional cell is initialized to the empty bag if
     * it contains no configuration variables, and to a single cell if it contains configuration variables.
     */
    private static KApply optionalCellInitializer(boolean initializeOptionalCell, String initLabel) {
        if (initializeOptionalCell) {
            return KApply(KLabel(initLabel), KVariable("Init"));
        } else {
            return KApply(KLabel("#cells"));
        }
    }

    private static Att getCellPropertiesAsAtt(K k, String cellName, K ensures) {
        Att att = Att();
        if (cellName.equals("k")) {
            att = att.add("maincell");
        }
        if (ensures != null) {
            att = att.add("topcell");
        }
        att = att.add("cell").add("klabel", "<" + cellName + ">");
        return att.addAll(getCellPropertiesAsAtt(k));
    }

    private static Att getCellPropertiesAsAtt(K k) {
        if (k instanceof KApply) {
            KApply kapp = (KApply) k;
            if (kapp.klabel().name().equals("#cellPropertyListTerminator")) {
                return Att();
            } else if (kapp.klabel().name().equals("#cellPropertyList")) {
                if (kapp.klist().size() == 2) {
                    Tuple2<String, String> attribute = getCellProperty(kapp.klist().items().get(0));
                    return Att().add(attribute._1(), attribute._2()).addAll(getCellPropertiesAsAtt(kapp.klist().items().get(1)));
                }
            }
        }
        throw KEMException.compilerError("Malformed cell properties", k);
    }

    private static Tuple2<String, String> getCellProperty(K k) {
        if (k instanceof KApply) {
            KApply kapp = (KApply) k;
            if (kapp.klabel().name().equals("#cellProperty")) {
                if (kapp.klist().size() == 2) {
                    if (kapp.klist().items().get(0) instanceof KToken) {
                        KToken keyToken = (KToken) kapp.klist().items().get(0);
                        if (keyToken.sort().equals(Sort("#CellName"))) {
                            String key = keyToken.s();
                            if (kapp.klist().items().get(0) instanceof KToken) {
                                KToken valueToken = (KToken) kapp.klist().items().get(1);
                                if (valueToken.sort().equals(Sort("KString"))) {
                                    String value = StringUtil.unquoteKString(valueToken.s());
                                    return Tuple2.apply(key, value);
                                }
                            }
                        }
                    }
                }
            }
        }
        throw KEMException.compilerError("Malformed cell property", k);
    }

    private static String getSortOfCell(String cellName) {
        char[] chars = cellName.toCharArray();
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(chars[0]));
        for (int i = 1; i < chars.length; i++) {
            if (chars[i] == '-' && i + 1 < chars.length && Character.isLowerCase(chars[i+1])) {
                chars[i+1] = Character.toUpperCase(chars[i+1]);
            } else if (chars[i] != '-') {
                sb.append(chars[i]);
            }
        }
        sb.append("Cell");
        return sb.toString();
    }

    private static Multiplicity convertStringMultiplicity(Option<String> multiplicity, K body) {
        if (multiplicity.isEmpty())
            return Multiplicity.ONE;
        switch(multiplicity.get()) {
        case "1":
            return Multiplicity.ONE;
        case "*":
            return Multiplicity.STAR;
        case "?":
            return Multiplicity.OPTIONAL;
        default:
            throw KEMException.compilerError("Invalid multiplicity found in cell: " + multiplicity.get(), body);
        }
    }
}
