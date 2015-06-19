package org.kframework.backend.func;

import org.kframework.kore.InjectedKLabel;
import org.kframework.attributes.Att;
import org.kframework.definition.Rule;
import org.kframework.definition.Module;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.KLabel;
import org.kframework.kore.Sort;
import org.kframework.kore.KLabel;
import org.kframework.kore.AbstractKORETransformer;
import org.kframework.backend.func.kst.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import scala.Tuple2;

import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public final class KOREtoKST {
    private KOREtoKST() {}

    private static KSTSort convertSort(Sort s) {
        return new KSTSort(s.name());
    }

    private static KSTLabel convertLabel(KLabel kl) {
        return new KSTLabel(kl.name());
    }

    private static KSTSyntax convertSyntax(KLabel kl,
                                           Sort s,
                                           List<Sort> a,
                                           Att att) {
        return new KSTSyntax(convertSort(s),
                             convertLabel(kl),
                             a.stream()
                              .map(srt -> new KSTSort(srt.name()))
                              .collect(Collectors.toList()),
                             convertAtt(att));
    }

    private static KSTRule convertRule(Rule r) {
        KOREtoKSTVisitor v = new KOREtoKSTVisitor();

        KSTTerm bod      = v.applyAsTerm(r.body());
        KSTTerm req      = v.applyAsTerm(r.requires());
        KSTTerm ens      = v.applyAsTerm(r.ensures());
        KSTAttSet atts   = convertAtt(r.att());

        return new KSTRule(bod, req, ens, atts);
    }

    private static KSTAttSet convertAtt(Att a) {
        KOREtoKSTVisitor v = new KOREtoKSTVisitor();
        Set<KSTAtt> as = a.stream()
                          .filter(x -> x instanceof KApply)
                          .map(v::applyAsApply)
                          .map(x -> new KSTAtt(x))
                          .collect(Collectors.toSet());
        return new KSTAttSet(as);
    }


    public static KSTModule convert(Module m) {
        Set<KSTModuleTerm> mts = new HashSet<>();
        KSTSyntax stx;
        KSTRule rl;

        Map<KLabel, scala.collection.immutable.Set<Tuple2<scala.collection.Seq<Sort>, Sort>>> rawSigs;
        Set<Tuple2<scala.collection.Seq<Sort>, Sort>> sigSet;
        rawSigs = scalaMapAsJava(m.signatureFor());
        Map<KLabel, Att> attMap = scalaMapAsJava(m.attributesFor());

        for(KLabel kl : rawSigs.keySet()) {
            sigSet = stream(rawSigs.get(kl)).collect(Collectors.toSet());

            for(Tuple2<scala.collection.Seq<Sort>, Sort> tup : sigSet) {
                stx = convertSyntax(kl,
                                    tup._2,
                                    stream(tup._1).collect(Collectors.toList()),
                                    attMap.get(kl));
                mts.add((KSTModuleTerm) stx);
            }
        }

        for(Rule r : stream(m.rules()).collect(Collectors.toList())) {
            rl = convertRule(r);
            mts.add((KSTModuleTerm) rl);
        }

        return new KSTModule(mts);
    }
}
