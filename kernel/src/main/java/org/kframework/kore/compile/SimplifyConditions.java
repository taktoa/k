package org.kframework.kore.compile;

import org.kframework.definition.Context;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;

import java.util.ArrayList;
import java.util.List;

import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Removes all spurious ANDs and ORs in side conditions
 */
public class SimplifyConditions {
    private static final K trueToken = KSequence(KToken("true", Sort("Bool")));
    private static final K falseToken = KSequence(KToken("false", Sort("Bool")));

    public synchronized Sentence convert(Sentence s) {
        if (s instanceof Rule) {
            return convert((Rule) s);
        } else if (s instanceof Context) {
            return convert((Context) s);
        } else {
            return s;
        }
    }

    private Rule convert(Rule rule) {
        return Rule(rule.body(),
                    convert(rule.requires()),
                    convert(rule.ensures()),
                    rule.att());
    }

    private Context convert(Context context) {
        return Context(context.body(),
                       convert(context.requires()),
                       context.att());
    }
    
    public K convert(K term) {
        K result = new TransformKORE() {
                @Override
                public K apply(KApply k) {
                    ArrayList<K> newItems = new ArrayList<>(k.klist().items());
                    boolean change = false;
                    int niSize = newItems.size();
                    K in, out, ret;
                    
                    for(int i = 0; i < niSize; ++i) {
                        in = newItems.get(i);
                        out = apply(in);
                        newItems.set(i, out);
                        change |= !in.equals(out);
                    }
                    
                    if(change) {
                        ret = KApply(k.klabel(), KList(newItems), k.att());
                    } else {
                        ret = k;
                    }
                    
                    if(niSize == 2) {
                        return checkSimplifications((KApply) ret,
                                                    newItems.get(0),
                                                    newItems.get(1));
                    } else {
                        return ret;
                    }
                }
        }.apply(term);
        return result;
    }

    private static K checkSimplifications(KApply tot, K fst, K snd) {
        String label = tot.klabel().toString();
        if("_andBool_".equals(label)) {
            return simplifyAnd(tot, fst, snd);
        }
        if("_orBool_".equals(label)) {
            return simplifyOr(tot, fst, snd);
        }
        return tot;
    }
    
    private static K simplifyAnd(K tot, K fst, K snd) {
        if(falseToken.equals(fst) || falseToken.equals(snd)) {
            return falseToken;
        } else if(trueToken.equals(fst)) {
            return snd;
        } else if(trueToken.equals(snd)) {
            return fst;
        } else {
            return tot;
        }
    }

    private static K simplifyOr(K tot, K fst, K snd) {
        if(trueToken.equals(fst) || trueToken.equals(snd)) {
            return trueToken;
        } else if(falseToken.equals(fst)) {
            return snd;
        } else if(falseToken.equals(snd)) {
            return fst;
        } else {
            return tot;
        }
    }
}
