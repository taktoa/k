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
import org.kframework.backend.func.kst.KSTRewrite;
import org.kframework.backend.func.kst.KSTSortAny;
import org.kframework.backend.func.kst.KSTLabelSeq;
import org.kframework.backend.func.kst.KSTLabelInj;

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
        KSTLabel l = applyAsLabel(k.klabel());
        for(K i : k.klist().items()) {
            a.add(applyAsTerm(i));
        }
        return toKST(new KSTApply(l, a));
    }

    @Override
    public KST apply(KRewrite k) {
        KSTTerm lhs = applyAsTerm(k.left());
        KSTTerm rhs = applyAsTerm(k.right());
        return toKST(new KSTRewrite(lhs, rhs));
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
            a.add(applyAsTerm(i));
        }
        return toKST(new KSTApply(new KSTLabelSeq(), a));
    }

    @Override
    public KST apply(InjectedKLabel k) {
        List<KSTTerm> a = new ArrayList<>();
        a.add(applyAsTerm(k.klabel()));
        return toKST(new KSTApply(new KSTLabelInj(), a));
    }

    public KST apply(KLabel k) {
        return toKST(new KSTLabel(k.name()));
    }

    public <T extends K> KSTTerm applyAsTerm(T k) {
        return coerceKST(KSTTerm.class, apply(k));
    }

    public <T extends K> KSTRewrite applyAsRewrite(T k) {
        return coerceKST(KSTRewrite.class, apply(k));
    }

    public <T extends K> KSTApply applyAsApply(T k) {
        return coerceKST(KSTApply.class, apply(k));
    }

    public <T extends K> KSTToken applyAsToken(T k) {
        return coerceKST(KSTToken.class, apply(k));
    }

    public <T extends K> KSTVariable applyAsVariable(T k) {
        return coerceKST(KSTVariable.class, apply(k));
    }

    public KSTTerm applyAsTerm(KLabel k) {
        return coerceKST(KSTTerm.class, apply(k));
    }

    public KSTLabel applyAsLabel(KLabel k) {
        return coerceKST(KSTLabel.class, apply(k));
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
}
