package org.kframework.backend.func;

import org.kframework.kore.InjectedKLabel;
import org.kframework.attributes.Att;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.KLabel;
import org.kframework.kore.AbstractKORETransformer;
import org.kframework.backend.func.kst.KST;
import org.kframework.backend.func.kst.KSTApply;
import org.kframework.backend.func.kst.KSTAtt;
import org.kframework.backend.func.kst.KSTLabel;
import org.kframework.backend.func.kst.KSTSort;
import org.kframework.backend.func.kst.KSTTerm;
import org.kframework.backend.func.kst.KSTToken;
import org.kframework.backend.func.kst.KSTVariable;
import org.kframework.backend.func.kst.KSTRule;
import org.kframework.backend.func.kst.sort.KSTSortAny;
import org.kframework.backend.func.kst.label.KSTLabelSeq;
import org.kframework.backend.func.kst.label.KSTLabelInj;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public class KOREtoKSTVisitor extends AbstractKORETransformer<KST> {
    @Override
    public KST apply(KApply k) {
        List<KSTTerm> a = new ArrayList<>();
        KSTLabel l = toKSTLabel(apply(k.klabel()));
        for(K i : k.klist().items()) {
            KST converted = apply(i);
            a.add(toKSTTerm(converted));
        }
        return toKST(new KSTApply(l, a));
    }

    @Override
    public KST apply(KRewrite k) {
        KSTTerm lhs = toKSTTerm(apply(k.left()));
        KSTTerm rhs = toKSTTerm(apply(k.right()));
        //        Set<KSTAtt> att = convertAttToKSTAtt(k.att());
        Set<KSTAtt> att = new HashSet<>();
        return toKST(new KSTRule(lhs, rhs, att));
    }

    @Override
    public KST apply(KToken k) {
        String t = k.s();
        KSTSort s = new KSTSort(k.sort().name());
        return toKST(new KSTToken(t, s));
    }

    @Override
    public KST apply(KVariable k) {
        return toKST(new KSTVariable(k.name(), new KSTSortAny()));
    }

    @Override
    public KST apply(KSequence k) {
        List<KSTTerm> a = new ArrayList<>();
        for(K i : k.items()) {
            a.add(toKSTTerm(apply(i)));
        }
        return toKST(new KSTApply(new KSTLabelSeq(), a));
    }

    @Override
    public KST apply(InjectedKLabel k) {
        List<KSTTerm> a = new ArrayList<>();
        a.add(toKSTTerm(apply(k.klabel())));
        return toKST(new KSTApply(new KSTLabelInj(), a));
    }

    public KST apply(KLabel k) {
        return toKST(new KSTLabel(k.name()));
    }

    public KSTAtt convertAttToKSTAtt(Att a) {
    //    List<KST> l = new ArrayList<>();
    //    l.add(new KST("att"));
    //    Map<String, KApply> am = scalaMapAsJava(k.attMap());
    //    for(String key : am.keySet()) {
    //        l.add(apply(am.get(key)));
    //    }
    //    return new KST(l);
        return new KSTAtt();
    }

    private <T extends KST> KST toKST(T k) {
        return (KST) k;
    }

    private <T extends KST> T coerceKST(Class<T> C, KST k) throws KEMException {
        if(C.isInstance(k)) {
            return C.cast(k);
        } else {
            throw KEMException.criticalError("Not instance of " + C.getName() + ": " + k.toString());
        }
    }

    private KSTTerm toKSTTerm(KST k) throws KEMException {
        return coerceKST(KSTTerm.class, k);
    }

    private KSTRule toKSTRule(KST k) throws KEMException {
        return coerceKST(KSTRule.class, k);
    }

    private KSTLabel toKSTLabel(KST k) throws KEMException {
        return coerceKST(KSTLabel.class, k);
    }

    private KSTApply toKSTApply(KST k) throws KEMException {
        return coerceKST(KSTApply.class, k);
    }

    private KSTToken toKSTToken(KST k) throws KEMException {
        return coerceKST(KSTToken.class, k);
    }

    private KSTVariable toKSTVariable(KST k) throws KEMException {
        return coerceKST(KSTVariable.class, k);
    }
}
