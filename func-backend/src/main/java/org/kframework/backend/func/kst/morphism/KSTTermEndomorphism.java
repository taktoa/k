package org.kframework.backend.func.kst;

import java.util.List;
import java.util.Set;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;
import org.kframework.utils.errorsystem.KEMException;

public class KSTTermEndomorphism implements UnaryOperator<KSTTerm> {
    private enum Dir {
        LEFT, RIGHT
    }

    private final KSTUtilCounter   applyDepth   = new KSTUtilCounter();
    private final List<KLabel>     applyPath    = new LinkedList<>();
    private final KSTUtilCounter   rewriteDepth = new KSTUtilCounter();
    private final List<Dir>        rewritePath  = new LinkedList<>();
    private final List<Set<KSTVariable>> scope  = new ArrayList<>().add(new HashSet<>());

    private final void rewriteLeft() {
        rewriteDepth.increment();
        rewritePath.add(LEFT);
    }

    private final void rewriteRight() {
        rewriteDepth.increment();
        rewritePath.add(RIGHT);
    }

    private final void exitRewrite() {
        rewriteDepth.decrement();
        rewritePath.remove(rewriteDepth.getCount());
    }

    protected final void addVariable(KSTVariable v) {
        listGetLast(scope).add(v);
    }

    protected final boolean isInScope(KSTVariable v) {
        listGetLast(scope).contains(v);
    }

    private final void saveScope() {
        listAppend(scope, listGetLast(scope));
    }

    private final void rewindScope() {
        listDropLast(scope);
    }
    
    @Override
    public final KSTTerm apply(KSTTerm t) {
        if(t instanceof KSTApply) {
            return apply((KSTApply) t);
        } else if(t instanceof KSTLabel) {
            return apply((KSTLabel) t);
        } else if(t instanceof KSTPrim) {
            return apply((KSTPrim) t);
        } else if(t instanceof KSTRewrite) {
            return apply((KSTRewrite) t);
        } else if(t instanceof KSTToken) {
            return apply((KSTToken) t);
        } else if(t instanceof KSTVariable) {
            return apply((KSTVariable) t);
        } else {
            throw KEMException.criticalError("Unknown subclass of KSTTerm: "
                                             + t.getClass().getName());
        }
    }

    public KSTTerm apply(KSTApply k) {
        KSTTerm resultLabelTerm = apply(k.getLabel());
        List<KSTTerm> resultArgs = k.getArgs()
                                    .stream()
                                    .map(this::apply)
                                    .collect(Collectors.toList());
        KSTLabel resultLabel;

        if(resultLabelTerm instanceof KSTLabel) {
            resultLabel = (KSTLabel) resultLabelTerm;
        } else {
            throw KEMException.criticalError("Cannot put "
                                             + resultLabelTerm.getClass().getName()
                                             + " as the label in a KSTApply");
        }

        return (KSTTerm) (new KSTApply(resultLabel, resultArgs, k.getSort()));
    }

    public KSTTerm apply(KSTLabel k) {
        return (KSTTerm) k;
    }

    public KSTTerm apply(KSTPrim k) {
        return (KSTTerm) k;
    }

    public final KSTTerm apply(KSTRewrite k) {
        KSTTerm lhs = k.getLHS();
        KSTTerm rhs = k.getRHS();
        KSTTerm resL, resR;
        saveScope();
        rewriteLeft();
        resL = apply(lhs);
        exitRewrite();
        rewriteRight();
        resR = apply(rhs);
        exitRewrite();
        rewindScope();
        return (KSTTerm) (new KSTRewrite(resL, resR));
    }

    public KSTTerm apply(KSTToken k) {
        return (KSTTerm) k;
    }

    public KSTTerm apply(KSTVariable k) {
        return (KSTTerm) k;
    }

    private static final <T> T listGetLast(List<T> l) {
        return l.get(l.size() - 1);
    }

    private static final <T> void listDropLast(List<T> l) {
        l.remove(l.size() - 1);
    }

    private static final <T> void listAppend(List<T> l, T e) {
        l.add(e);
    }
}
