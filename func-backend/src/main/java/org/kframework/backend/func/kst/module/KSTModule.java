package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.stream.Collectors;

public class KSTModule {
    private final Set<KSTModuleTerm> modTerms;

    public KSTModule(Set<KSTModuleTerm> t) {
        modTerms = t;
    }

    public Set<KSTModuleTerm> getTerms() {
        return modTerms;
    }

    public String toString() {
        return modTerms.stream()
                       .map(x -> x.toString())
                       .collect(Collectors.joining("\n",
                                                   "module:\n",
                                                   "\n"));
    }
}
