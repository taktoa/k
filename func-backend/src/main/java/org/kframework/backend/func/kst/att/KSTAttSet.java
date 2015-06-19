package org.kframework.backend.func.kst;

import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Collection;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.kframework.utils.errorsystem.KEMException;

public class KSTAttSet {
    private final Set<KSTAtt> attSet;
    private final Map<KSTLabel, KSTAtt> attLabelMap;

    public KSTAttSet() {
        attSet = Sets.newHashSet();
        attLabelMap = Maps.newHashMap();
    }

    public KSTAttSet(Collection<KSTAtt> atts) {
        this();
        merge(atts);
    }

    public KSTAttSet(KSTAttSet atts) {
        this(atts.getAttSet());
    }

    protected void addAtt(KSTAtt a) {
        attSet.add(a);
        attLabelMap.put(a.getLabel(), a);
        if(attSet.size() != attLabelMap.size()) {
            throw KEMException.criticalError("Duplicate label added to attribute set");
        }
    }

    public Set<KSTAtt> getAttSet() {
        return attSet;
    }

    public Optional<KSTAtt> get(KSTLabel l) {
        return Optional.ofNullable(attLabelMap.get(l));
    }

    public Optional<KSTAtt> get(String l) {
        return get(new KSTLabel(l));
    }

    public Set<KSTLabel> labelSet() {
        return attLabelMap.keySet();
    }

    public void merge(Collection<KSTAtt> atts) {
        for(KSTAtt a : atts) {
            addAtt(a);
        }
    }

    public void merge(KSTAttSet atts) {
        merge(atts.getAttSet());
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof KSTAttSet) {
            KSTAttSet a = (KSTAttSet) o;
            return attSet.equals(a.getAttSet());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return attSet.hashCode();
    }

    @Override
    public String toString() {
        return attSet.toString();
    }
}
