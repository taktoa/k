package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class KSTModule {
    private final Set<KSTModuleTerm> modTerms;

    public KSTModule(Set<KSTModuleTerm> modTerms) {
        this.modTerms = modTerms;
    }

    public Set<KSTModuleTerm> getTerms() {
        return modTerms;
    }

    @Override
    public String toString() {
        Map<String, List<KSTModuleTerm>> modTermMap
            = modTerms.stream().collect(Collectors.groupingBy(KSTModule::getModTermClass));
        List<KSTModuleTerm> sorted = new ArrayList<>(modTerms.size());

        for(String s : modTermMap.keySet()) {
            for(KSTModuleTerm mt : modTermMap.get(s)) {
                sorted.add(mt);
            }
        }

        return sorted.stream()
                     .map(x -> x.toString())
                     .collect(Collectors.joining("\n", "module:\n", "\n"));
    }

    private static String getModTermClass(KSTModuleTerm kmt) {
        return kmt.getClass().getName().toString();
    }
}
