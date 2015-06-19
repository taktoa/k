package org.kframework.backend.func.kst;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.stream.Collectors;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.function.BinaryOperator;
import org.kframework.utils.errorsystem.KEMException;

public class KSTModuleView {
    private final KSTModule module;

    public KSTModuleView(KSTModule module) {
        this.module = module;
    }

    public KSTModule getModule() {
        return module;
    }

    public Set<KSTLabel> functionSet() {
        return module.getTerms()
                     .stream()
                     .filter(x -> x instanceof KSTNormFunction)
                     .map(x -> (KSTNormFunction) x)
                     .map(x -> x.getLabel())
                     .collect(Collectors.toSet());
    }

    public Map<KSTLabel, List<KSTNormFunction>> functionRulesOrdered() {
        return module.getTerms()
                     .stream()
                     .filter(x -> x instanceof KSTNormFunction)
                     .map(x -> (KSTNormFunction) x)
                     .collect(Collectors.groupingBy(x -> x.getLabel()));
    }

    public Set<KSTSort> definedSorts() {
        return module.getTerms()
                     .stream()
                     .filter(x -> x instanceof KSTType)
                     .map(x -> (KSTType) x)
                     .map(x -> x.getSort())
                     .collect(Collectors.toSet());
    }

    public Set<KSTLabel> definedLabels() {
        return module.getTerms()
                     .stream()
                     .filter(x -> x instanceof KSTType)
                     .map(x -> (KSTType) x)
                     .flatMap(x -> x.getConstructors().keySet().stream())
                     .collect(Collectors.toSet());
    }

    private static <A, B> Map<A, B> mapMerge(Map<A, B> m1, Map<A, B> m2) {
        Map<A, B> res = Maps.newHashMap();
        res.putAll(m1);
        res.putAll(m2);
        return res;
    }

    private Map<KSTLabel, KSTAttSet> labelAtts() {
        return module.getTerms()
                     .stream()
                     .filter(x -> x instanceof KSTType)
                     .map(x -> (KSTType) x)
                     .map(x -> x.getConstructorAtts())
                     .collect(Collectors.reducing(KSTModuleView::mapMerge))
                     .orElse(Maps.newHashMap());
    }

    public Map<String, Map<KSTLabel, String>> attrLabels() {
        Map<String, Map<KSTLabel, String>> result = Maps.newHashMap();
        Map<KSTLabel, KSTAttSet> la = labelAtts();

        for(KSTLabel l : la.keySet()) {
            for(KSTAtt a : la.get(l).getAttSet()) {
                result.put(a.getLabel().getName(), Maps.newHashMap());
            }
        }

        for(KSTLabel l : la.keySet()) {
            for(String a : result.keySet()) {
                Optional<String> oa = la.get(l)
                                        .get(a)
                                        .map(x -> x.getArgs())
                                        .filter(x -> x.size() == 1)
                                        .map(x -> x.get(0))
                                        .filter(x -> x instanceof KSTToken)
                                        .map(x -> (KSTToken) x)
                                        .map(x -> x.getToken());
                if(oa.isPresent()) {
                    result.get(a).put(l, oa.get());
                }
            }
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTModuleView) {
            KSTModuleView k = (KSTModuleView) o;
            return module.equals(k.module);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return module.hashCode();
    }

    @Override
    public String toString() {
        return module.toString();
    }
}
