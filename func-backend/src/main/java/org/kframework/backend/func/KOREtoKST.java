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
import org.kframework.backend.func.kst.KST;
import org.kframework.backend.func.kst.KSTApply;
import org.kframework.backend.func.kst.KSTAtt;
import org.kframework.backend.func.kst.KSTLabel;
import org.kframework.backend.func.kst.KSTSort;
import org.kframework.backend.func.kst.KSTTerm;
import org.kframework.backend.func.kst.KSTToken;
import org.kframework.backend.func.kst.KSTVariable;
import org.kframework.backend.func.kst.KSTRewrite;
import org.kframework.backend.func.kst.KSTRule;
import org.kframework.backend.func.kst.KSTSyntax;
import org.kframework.backend.func.kst.KSTModule;
import org.kframework.backend.func.kst.KSTSortAny;
import org.kframework.backend.func.kst.KSTLabelSeq;
import org.kframework.backend.func.kst.KSTLabelInj;

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
    private int freshNum = 0;

    //    private KOREtoKST() {}

    private static KSTSort convertSort(Sort s) {
        return new KSTSort(s.name());
    }

    private static KSTLabel convertLabel(KLabel kl) {
        return new KSTLabel(kl.name());
    }
    
    private static KSTSyntax convertSyntax(KLabel kl, Sort s, List<Sort> a) {
        return new KSTSyntax(convertSort(s),
                             convertLabel(kl),
                             a.stream()
                              .map(srt -> new KSTSort(srt.name()))
                              .collect(Collectors.toList()));
    }

    private static KSTRule convertRule(Rule r) {
        KOREtoKSTVisitor v = new KOREtoKSTVisitor();

        KSTTerm bod = v.applyAsTerm(r.body());
        KSTTerm req = v.applyAsTerm(r.requires());
        KSTTerm ens = v.applyAsTerm(r.ensures());
        Set<KSTAtt> atts = new HashSet<>();

        return new KSTRule(bod, req, ens, atts);
    }
    
    public static KSTModule convert(Module m) {
        Set<KSTSyntax> stx = new HashSet<>();
        Set<KSTRule> rls = new HashSet<>();
        
        Map<KLabel, scala.collection.immutable.Set<Tuple2<scala.collection.Seq<Sort>, Sort>>> rawSigs;
        Set<Tuple2<scala.collection.Seq<Sort>, Sort>> sigSet;
        rawSigs = scalaMapAsJava(m.signatureFor());
        
        for(KLabel kl : rawSigs.keySet()) {
            sigSet = stream(rawSigs.get(kl)).collect(Collectors.toSet());
            
            for(Tuple2<scala.collection.Seq<Sort>, Sort> tup : sigSet) {
                stx.add(convertSyntax(kl, tup._2, stream(tup._1).collect(Collectors.toList())));
            }
        }

        for(Rule r : stream(m.rules()).collect(Collectors.toList())) {
            rls.add(convertRule(r));
        }

        return new KSTModule(stx, rls);
    }

    // -------------------------------

    public String sortCheckStrMod(KSTModule m) {
        String stx = m.getSyntax()
                      .stream()
                      .map(this::sortCheckStrSyn)
                      .collect(Collectors.joining("\n", "", "\n"));

        String rls = m.getRules()
                      .stream()
                      .map(this::sortCheckStrRule)
                      .collect(Collectors.joining("\n", "", "\n"));

        return stx + rls;
    }
    
    private String sortCheckStrSyn(KSTSyntax t) {
        String pfx = "'" + t.getLabel().getName() + " ~ ";
        String del = " -> ";
        String sfx = del + t.getSort().getName();
        return t.getArgs()
                .stream()
                .map(x -> x.toString())
                .collect(Collectors.joining(del, pfx, sfx));
    }

    private String sortCheckStrRule(KSTRule r) {
        String bd = sortCheckStr(r.getBody());
        String re = condCheckStr(r.getRequires());
        String en = condCheckStr(r.getEnsures());
        return String.format("scope {\n%s\n} where {\n%s\n%s\n};", bd, re, en);
    }


    private String freshVarName() {
        freshNum++;
        return String.format("'fresh%d", freshNum);
    }

    private String sortCheckStr(KSTTerm t) {
        if(t instanceof KSTApply) {
            return sortCheckStr((KSTApply) t);
        } else if(t instanceof KSTLabel) {
            return sortCheckStr((KSTLabel) t);
        } else if(t instanceof KSTRewrite) {
            return sortCheckStr((KSTRewrite) t);
        } else if(t instanceof KSTToken) {
            return sortCheckStr((KSTToken) t);
        } else if(t instanceof KSTVariable) {
            return sortCheckStr((KSTVariable) t);
        } else {
            throw KEMException.criticalError("Unknown subclass of KSTTerm: "
                                             + t.getClass().getName());
        }
    }

    private String sortCheckStr(KSTRewrite r) {
        String var1 = freshVarName();
        String var2 = freshVarName();
        return String.format("%s ~ %s\n%s ~ %s\n%s ~ %s",
                             var1,
                             var2,
                             var1,
                             sortCheckStr(r.getLHS()),
                             var2,
                             sortCheckStr(r.getRHS()));
    }

    private String sortCheckStr(KSTApply a) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        String v;
        for(int i = 0; i < a.getArgs().size(); i++) {
            sb2.append("(");
        }
        sb2.append("'");
        sb2.append(a.getLabel());
        for(KSTTerm t : a.getArgs()) {
            v = freshVarName();
            sb2.append(" " + v + ")");
            sb.append(v);
            sb.append(" ~ { ");
            sb.append(sortCheckStr(t));
            sb.append(" }");
            sb.append("\n");
        }
        return String.format("%s\n\n%s", sb2, sb);
    }

    private String sortCheckStr(KSTVariable t) {
        return leftoverStr((KSTTerm) t, "'" + t.getName());
    }
    
    private String sortCheckStr(KSTToken t) {
        return leftoverStr((KSTTerm) t, freshVarName());
    }
    
    private String sortCheckStr(KSTLabel t) {
        return leftoverStr((KSTTerm) t, freshVarName());
    }

    private String leftoverStr(KSTTerm t, String f) {
        String sn = t.getSort().getName();
        return ("Any".equals(sn) ? f : sn);
    }

    private String condCheckStr(KSTTerm t) {
        return String.format("Bool ~ %s", sortCheckStr(t));
    }
}
