// Copyright (c) 2015 K Team. All Rights Reserved.

package org.kframework.kore.compile;

import org.kframework.attributes.Att;
import org.kframework.builtin.BooleanUtils;
import org.kframework.definition.Context;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kore.Assoc;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KVariable;
import org.kframework.utils.errorsystem.KEMException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Created by dwightguth on 5/12/15.
 */
public class ConvertDataStructureToLookup {


    private Set<KApply> state = new HashSet<>();
    private Set<KVariable> vars = new HashSet<>();

    void reset() {
        state.clear();
        vars.clear();
    }

    private final Module m;

    public ConvertDataStructureToLookup(Module m) {
        this.m = m;
    }

    private Rule convert(Rule rule) {
        reset();
        gatherVars(rule.body());
        gatherVars(rule.requires());
        gatherVars(rule.ensures());
        K body = transform(rule.body());
        return Rule(
                body,
                addSideCondition(rule.requires()),
                rule.ensures(),
                rule.att());
    }

    private Context convert(Context context) {
        reset();
        gatherVars(context.body());
        gatherVars(context.requires());
        K body = transform(context.body());
        return new Context(
                body,
                addSideCondition(context.requires()),
                context.att());
    }

    void gatherVars(K term) {
        new VisitKORE() {
            @Override
            public Void apply(KVariable v) {
                vars.add(v);
                return super.apply(v);
            }
        }.apply(term);
    }

    K addSideCondition(K requires) {
        Optional<KApply> sideCondition = state.stream().reduce(BooleanUtils::and);
        if (!sideCondition.isPresent()) {
            return requires;
        } else if (requires.equals(BooleanUtils.TRUE) && sideCondition.isPresent()) {
            return sideCondition.get();
        } else {
            return BooleanUtils.and(requires, sideCondition.get());
        }
    }

    private int counter = 0;
    KVariable newDotVariable() {
        KVariable newLabel;
        do {
            newLabel = KVariable("_" + (counter++));
        } while (vars.contains(newLabel));
        vars.add(newLabel);
        return newLabel;
    }

    private KEMException unsupportedSetCollectionError(KApply k) {
        return KEMException.internalError("Unsupported collection type: Set", k);
    }

    private KEMException unsupportedListCollectionError(KApply k) {
        return KEMException.internalError("Unsupported collection type: List", k);
    }
    
    private KEMException associativeMapMatchingError(K component, KVariable frame, KApply k) {
        return KEMException.internalError("Unsupported associative matching on Map. Found variables " + component + " and " + frame, k);
    }

    private KEMException unexpectedMapElementArityError(KApply kapp) {
        return KEMException.internalError("Unexpected arity of map element: " + kapp.klist().size(), kapp);
    }

    private KEMException unexpectedMapTermError(KApply kapp) {
        return KEMException.internalError("Unexpected term in map, not a map element.", kapp);
    }
    
    void checkIfList(Att att, KApply k) {
        if(! att.contains(Attribute.COMMUTATIVE_KEY))
            throw unsupportedListCollectionError(k);
    }

    void checkIfSet(Att att, KApply k) {
        if(att.contains(Attribute.IDEMPOTENT_KEY))
            throw unsupportedSetCollectionError(k);
    }

    void checkMapTerm(KApply kapp, KApply k) {
        KLabel a = kapp.klabel();
        KLabel b = KLabel(m.attributesFor().apply(k.klabel()).<String>get("element").get());
        if(! a.equals(b))
            throw unexpectedMapTermError(kapp);
    }

    void checkMapArity(KApply kapp) {
        if(kapp.klist().size() != 2)
            throw unexpectedMapElementArityError(kapp);
    }

    void checkAssocMap(K component, KVariable frame, KApply k) {
        if(frame != null)
            throw associativeMapMatchingError(component, frame, k);
    }
    
    K transform(K term) {
        return new TransformKORE() {
            @Override
            public K apply(KApply k) {
                Att att = m.attributesFor().apply(k.klabel());
                if(! att.contains(Attribute.ASSOCIATIVE_KEY))
                    return super.apply(k);
                List<K> components = Assoc.flatten(k.klabel(), k.klist().items(), m);
                checkIfList(att, k);
                checkIfSet(att, k);
                //TODO(dwightguth): differentiate Map and Bag
                if(rhsOf != null)
                    return super.apply(k);
                KVariable frame = null;
                Map<K, K> elements = new LinkedHashMap<>();
                for (K component : components) {
                    if (component instanceof KVariable) {
                        checkAssocMap(component, frame, k);
                        frame = (KVariable) component;
                    } else if (component instanceof KApply) {
                        KApply kapp = (KApply) component;
                        checkMapTerm(kapp, k);
                        checkMapArity(kapp);
                        elements.put(kapp.klist().items().get(0), kapp.klist().items().get(1));
                    }
                    // Shouldn't there be an else here???
                }
                KVariable map = newDotVariable();
                if (frame != null) {
                    state.add(KApply(KLabel("#match"), frame, elements.keySet().stream().reduce(map, (a1, a2) -> KApply(KLabel("_[_<-undef]"), a1, a2))));
                }
                for (Map.Entry<K, K> element : elements.entrySet()) {
                    state.add(KApply(KLabel("#match"), RewriteToTop.toLeft(element.getValue()), KApply(KLabel("Map:lookup"), map, element.getKey())));
                }
                if (lhsOf == null) {
                    return KRewrite(map, RewriteToTop.toRight(k));
                } else {
                    return map;
                }
            }

            private K lhsOf;
            private K rhsOf;

            @Override
            public K apply(KRewrite k) {
                lhsOf = k;
                K l = apply(k.left());
                lhsOf = null;
                rhsOf = k;
                K r = apply(k.right());
                rhsOf = null;
                if (l != k.left() || r != k.right()) {
                    return KRewrite(l, r, k.att());
                } else {
                    return k;
                }
            }
        }.apply(term);
    }


    public synchronized Sentence convert(Sentence s) {
        if (s instanceof Rule) {
            return convert((Rule) s);
        } else if (s instanceof Context) {
            return convert((Context) s);
        } else {
            return s;
        }
    }
}
