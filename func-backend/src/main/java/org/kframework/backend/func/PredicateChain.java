// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import java.util.function.Predicate;

import com.google.common.collect.ComparisonChain;

/**
 * A chain of comparisons using the given predicates.
 * Similar to a {@link ComparisonChain}.
 *
 * @author Remy Goldschmidt
 */
public final class PredicateChain<T> {
    private int result = 0;
    private final T left;
    private final T right;

    private PredicateChain(T left, T right) {
        this.left  = left;
        this.right = right;
    }

    public static <E> PredicateChain<E> start(E l, E r) {
        return new PredicateChain<E>(l, r);
    }

    public PredicateChain<T> comparePredF(Predicate<T> pred) {
        return compareFalseFirst(pred.test(left),
                                 pred.test(right));
    }

    public PredicateChain<T> comparePredT(Predicate<T> pred) {
        return compareTrueFirst(pred.test(left),
                                pred.test(right));
    }

    public PredicateChain<T> compareFalseFirst(boolean left, boolean right) {
        if(result == 0 && left != right) {
            if(left)  { result = -1; }
            if(right) { result = 1;  }
        }
        return this;
    }

    public PredicateChain<T> compareTrueFirst(boolean left, boolean right) {
        if(result == 0 && left != right) {
            if(left)  { result = 1; }
            if(right) { result = -1;  }
        }
        return this;
    }

    public int result() {
        return result;
    }
}
