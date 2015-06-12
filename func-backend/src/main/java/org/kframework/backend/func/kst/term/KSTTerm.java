package org.kframework.backend.func.kst;

import java.util.Set;
import java.util.HashSet;

public class KSTTerm extends KST {
    private KSTSort sort = new KSTSortAny();
    private Set<KSTVariable> scope = new HashSet<>();

    public KSTSort getSort() {
        return sort;
    }

    public Set<KSTVariable> getScope() {
        return scope;
    }

    public boolean isInScope(KSTVariable v) {
        return scope.contains(v);
    }

    protected void setSort(KSTSort s) {
        sort = s;
    }

    protected void setScope(Set<KSTVariable> s) {
        scope = s;
    }

    protected void addScopeVariable(KSTVariable v) {
        scope.add(v);
    }

    protected void resetScope() {
        setScope(new HashSet<>());
    }
}
