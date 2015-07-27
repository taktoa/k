package org.kframework.kore.compile;

import org.kframework.definition.Context;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;

import java.util.ArrayList;

import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Removes all spurious ANDs and ORs in side conditions
 */
public class SimplifyConditions {
    private static final K trueToken = KSequence(KToken("true", Sort("Bool")));
    private static final K falseToken = KSequence(KToken("false", Sort("Bool")));

    public Sentence convert(Sentence s) {
        if (s instanceof Rule) {
            return convert((Rule) s);
        } else if (s instanceof Context) {
            return convert((Context) s);
        } else {
            return s;
        }
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

                    String label = ((KApply) ret).klabel().toString();

                    if(niSize == 2) {
                        if("_andBool_".equals(label)) {
                            return simplifyAnd(newItems.get(0),
                                               newItems.get(1));
                        }

                        if("_orBool_".equals(label)) {
                            return simplifyOr(newItems.get(0),
                                              newItems.get(1));
                        }
                    } else if(niSize == 1) {
                        if("notBool_".equals(label)) {
                            return simplifyNot(newItems.get(0));
                        }
                    }

                    return ret;
                }
        }.apply(term);
        return result;
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

    private static K simplifyNot(K val) {
        if(val instanceof KApply) {
            KApply kapp = (KApply) val;
            String label = kapp.klabel().toString();
            if("_andBool_".equals(label)) {
                return KApply(KLabel("_orBool_"),
                              KApply(KLabel("notBool_"),
                                     kapp.klist().items().get(0)),
                              KApply(KLabel("notBool_"),
                                     kapp.klist().items().get(1)));
            } else if("_orBool_".equals(label)) {
                return KApply(KLabel("_andBool_"),
                              KApply(KLabel("notBool_"),
                                     kapp.klist().items().get(0)),
                              KApply(KLabel("notBool_"),
                                     kapp.klist().items().get(1)));
            } else if("notBool_".equals(label)) {
                return kapp.klist().items().get(0);
            }
        }

        return KApply(KLabel("notBool_"), val);
    }

    private static K simplifyAnd(K fst, K snd) {
        if(falseToken.equals(fst) || falseToken.equals(snd)) {
            return falseToken;
        } else if(trueToken.equals(fst)) {
            return snd;
        } else if(trueToken.equals(snd)) {
            return fst;
        } else if(fst.equals(snd)) {
            return fst;
        } else {
            return KApply(KLabel("_andBool_"), fst, snd);
        }
    }

    private static K simplifyOr(K fst, K snd) {
        if(trueToken.equals(fst) || trueToken.equals(snd)) {
            return trueToken;
        } else if(falseToken.equals(fst)) {
            return snd;
        } else if(falseToken.equals(snd)) {
            return fst;
        } else if(fst.equals(snd)) {
            return fst;
        } else {
            return KApply(KLabel("_orBool_"), fst, snd);
        }
    }
}
