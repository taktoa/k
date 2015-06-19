package org.kframework.backend.func.kst;

import java.util.function.Function;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList;
import org.kframework.utils.errorsystem.KEMException;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class NormalizeFunctions implements UnaryOperator<KSTModule> {
    public static UnaryOperator<KSTModule> getModuleEndo() {
        return (UnaryOperator<KSTModule>) new NormalizeFunctions();
    }

    @Override
    public KSTModule apply(KSTModule k) {
        Set<KSTModuleTerm> nonFunctions
            = k.getTerms()
               .stream()
               .filter(x -> !(x instanceof KSTFunction))
               .collect(Collectors.toSet());
        Set<KSTFunction> functions
            = k.getTerms()
               .stream()
               .filter(x -> x instanceof KSTFunction)
               .map(x -> (KSTFunction) x)
               .collect(Collectors.toSet());
        Map<KSTLabel, List<KSTFunction>> functionMap
            = functions.stream().collect(Collectors.groupingBy(x -> x.getLabel()));
        Set<KSTNormFunction> normFunctions
            = Sets.newHashSetWithExpectedSize(functionMap.keySet().size());
        for(KSTLabel l : functionMap.keySet()) {
            List<KSTFunction> funcs = functionMap.get(l);
            List<List<KSTPattern>> pats = Lists.newArrayListWithCapacity(funcs.size());
            List<KSTExpr>          vals = Lists.newArrayListWithCapacity(funcs.size());
            KSTSort srt = funcs.get(0).getSort();
            KSTSort rs  = funcs.get(0).getResultSort();
            for(KSTFunction f : funcs) {
                if(!srt.equals(f.getSort())) {
                    throw KEMException.criticalError("NormalizeFunctions: sort does not match");
                }

                pats.add(f.getArgs());
                vals.add(new KSTTermExpr(f.getBody()));
            }
            KSTMatch match = new KSTMatch(pats, vals);
            normFunctions.add(new KSTNormFunction(l, rs, ImmutableList.of(), match));
        }
        int totalSize = nonFunctions.size() + normFunctions.size();
        Set<KSTModuleTerm> result = Sets.newHashSetWithExpectedSize(totalSize);
        result.addAll(nonFunctions);
        result.addAll(normFunctions);
        return new KSTModule(result);
    }
}
