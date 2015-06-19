package org.kframework.backend.func.kst;

import java.util.function.Function;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class SyntaxToType implements UnaryOperator<KSTModule> {
    public static UnaryOperator<KSTModule> getModuleEndo() {
        return (UnaryOperator<KSTModule>) new SyntaxToType();
    }

    @Override
    public KSTModule apply(KSTModule k) {
        Set<KSTModuleTerm> mts = k.getTerms();
        Map<Boolean, List<KSTModuleTerm>> isSyntax
            = mts.stream().collect(Collectors.groupingBy(x -> Boolean.valueOf(x instanceof KSTSyntax)));
        Set<KSTSort> sorts;
        Map<String, List<KSTSyntax>> mstx;
        mstx = isSyntax.get(Boolean.TRUE)
                       .stream()
                       .map(x -> (KSTSyntax) x)
                       .collect(Collectors.groupingBy(x -> x.getSort().getName()));

        Set<KSTModuleTerm> res = isSyntax.get(Boolean.FALSE)
                                         .stream()
                                         .collect(Collectors.toSet());

        for(String srt : mstx.keySet()) {
            Map<KSTLabel, List<KSTSort>> cons = Maps.newHashMap();
            Map<KSTLabel, KSTAttSet>  conAtts = Maps.newHashMap();
            for(KSTSyntax stx : mstx.get(srt)) {
                cons.put(stx.getLabel(), stx.getArgs());
                conAtts.put(stx.getLabel(), stx.getAtts());
            }
            res.add((KSTModuleTerm) new KSTType(new KSTSort(srt), cons, conAtts));
        }

        return new KSTModule(res);
    }
}
