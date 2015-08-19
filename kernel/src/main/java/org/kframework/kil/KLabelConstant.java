// Copyright (c) 2013-2015 K Team. All Rights Reserved.

package org.kframework.kil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.kframework.compile.transformers.AddPredicates;
import org.kframework.kil.loader.Constants;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.Visitor;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.w3c.dom.Element;

/**
 * AST representation of a KLabel constant.
 */
public class KLabelConstant extends KLabel {

    /**
     * HashMap caches the constants to ensure uniqueness.
     */
    private static final ConcurrentHashMap<String, KLabelConstant> cache = new ConcurrentHashMap<>();

    /*
     * Useful constants.
     */
    public static final KLabelConstant COOL_KLABEL = of("cool");
    public static final KLabelConstant HEAT_KLABEL = of("heat");
    public static final KLabelConstant HEATED_KLABEL = of("heated");
    public static final KLabelConstant REDEX_KLABEL = of("redex");
    public static final KLabelConstant KNEQ_KLABEL = of("'_=/=K_");
    public static final KLabelConstant KEQ_KLABEL = of("'_==K_");
    public static final KLabelConstant KEQ = of("'_=K_");
    public static final KLabelConstant KLIST_EQUALITY = of("'_=" + Sort.KLIST + "_");
    public static final KLabelConstant ANDBOOL_KLABEL = of("'#andBool");
    public static final KLabelConstant BOOL_ANDBOOL_KLABEL = of("'_andBool_");
    public static final KLabelConstant NOTBOOL_KLABEL = of("'notBool_");
    public static final KLabelConstant BOOL_ANDTHENBOOL_KLABEL = of("'_andThenBool_");
    public static final KLabelConstant KRESULT_PREDICATE = of(AddPredicates.predicate(Sort.KRESULT));
    public static final KLabelConstant STREAM_PREDICATE = of(AddPredicates.predicate(Sort.of("Stream")));
    public static final KLabelConstant STRING_PLUSSTRING_KLABEL = of("'_+String_");
    public static final KLabelConstant FRESH_KLABEL = of("fresh");

    /**
     * Static function for creating AST term representation of KLabel constants. The function caches the KLabelConstant objects; subsequent calls with the same label return
     * the same object. As opposed to "of", does not inspect for list of productions. Should be used for builtins only.
     *
     * @param label
     *            string representation of the KLabel; must not be '`' escaped;
     * @return AST term representation the KLabel;
     */
    public static KLabelConstant of(String label) {
        assert label != null;
        return cache.computeIfAbsent(label, x -> new KLabelConstant(x));
    }

    /**
     * Checks whether this label corresponds to a predicate
     * @return true if the label denotes a predicate or false otherwise
     */
    public boolean isPredicate() {
        return  label.startsWith("is");
    }

    public String getLabel() {
        return label;
    }

    /* un-escaped label */
    private final String label;

    public KLabelConstant(String label) {
        this.label = label;
    }

    /**
     * Constructs a {@link KLabelConstant} from an XML {@link Element} representing a constant. The KLabel string representation in the element is escaped according to Maude
     * conventions.
     */
    public KLabelConstant(Element element) {
        super(element);
        label = StringUtil.unescapeMaude(element.getAttribute(Constants.VALUE_value_ATTR));
    }

    @Override
    public Term shallowCopy() {
        /* this object is immutable */
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof KLabelConstant)) {
            return false;
        }

        KLabelConstant kLabelConstant = (KLabelConstant) object;
        return label.equals(kLabelConstant.label);
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public String toString() {
        return getLabel();
    }

    /**
     * A KLabel is considered functional if either it syntactically qualifies as a predicate,
     * or if the attributes associated to its production contain
     * @param context  the definitional context in which the labels should be considered.
     * @return  whether this KLabel is functional or not in the given {@code context}
     */
    public boolean isFunctional(Context context) {
        if (isPredicate()) {
            return true;
        } else {
            Set<Production> productions = context.productionsOf(getLabel());
            Production functionProduction = null;
            for (Production production : productions) {
                if (production.containsAttribute(Attribute.FUNCTION_KEY)
                        || production.containsAttribute(Attribute.PREDICATE_KEY)) {
                    functionProduction = production;
                } else if (functionProduction != null) {  // this label can either be function or not.
                    throw KExceptionManager.criticalError(
                            "Ambiguity: Top symbol " + label + " corresponds to both a functional declaration (" +
                                    functionProduction + ") and to a non-functional one (" +
                                    production + ")",
                            this);
                }
            }
            if (functionProduction != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }
}
