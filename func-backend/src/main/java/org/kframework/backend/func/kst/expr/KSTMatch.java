package org.kframework.backend.func.kst;

import java.util.Optional;
import java.util.List;
import java.util.function.*;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import org.kframework.utils.errorsystem.KEMException;

public final class KSTMatch extends KSTExpr {
    private final List<List<KSTPattern>> pats;
    private final List<KSTExpr> vals;

    public KSTMatch(List<List<KSTPattern>> pats,
                    List<KSTExpr> vals) {
        if(pats.size() != vals.size()) {
            throw KEMException.criticalError("KSTMatch: pattern and value list lengths do not match");
        }

        this.pats = pats;
        this.vals = vals;
    }

    public List<List<KSTPattern>> getPats() {
        return pats;
    }

    public List<KSTExpr> getVals() {
        return vals;
    }

    private int size() {
        return pats.size();
    }

    public <T> List<T> indexedMap(Function<Integer, BiFunction<List<KSTPattern>, KSTExpr, T>> func) {
        List<T> result = Lists.newArrayListWithCapacity(size());
        for(int i = 0; i < size(); i++) {
            result.add(func.apply(Integer.valueOf(i)).apply(pats.get(i), vals.get(i)));
        }
        return result;
    }

    public <T> List<T> map(BiFunction<List<KSTPattern>, KSTExpr, T> func) {
        return indexedMap(i -> func);
    }

    public <T> List<T> mapPats(Function<List<KSTPattern>, T> func) {
        return map((p, v) -> func.apply(p));
    }

    public <T> List<T> mapVals(Function<KSTExpr, T> func) {
        return map((p, v) -> func.apply(v));
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTMatch) {
            KSTMatch km = (KSTMatch) o;
            List<List<KSTPattern>> kmPats = km.getPats();
            List<KSTExpr> kmVals = km.getVals();
            List<Boolean> bools = indexedMap(i ->
                                             (p, v) ->
                                             Boolean.valueOf((p.equals(kmPats.get(i))) &&
                                                             (v.equals(kmVals.get(i)))));
            return ! bools.stream().collect(Collectors.toSet()).contains(Boolean.FALSE);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return pats.hashCode() + 2 * vals.hashCode();
    }

    @Override
    public String toString() {
        String equations = map((p, v) -> String.format("(%s -> %s)", p, v))
                              .stream()
                              .collect(Collectors.joining("\n       "));
        return String.format("(match %s)", equations);
    }
}
