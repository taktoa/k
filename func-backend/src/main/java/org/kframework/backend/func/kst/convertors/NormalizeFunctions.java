package org.kframework.backend.func.kst;

import java.util.function.Function;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.google.common.collect.Sets;
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
        return new KSTModule(k.getTerms()
                              .stream()
                              .map(x -> moduleTermTransformer(x))
                              .collect(Collectors.toSet()));
    }

    private static KSTModuleTerm moduleTermTransformer(KSTModuleTerm kmt) {
        return kmt;
    }
}
