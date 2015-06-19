package org.kframework.backend.func;

import org.kframework.backend.func.kst.KSTApply;
import org.kframework.backend.func.kst.KSTLabel;
import org.kframework.backend.func.kst.KSTTerm;
import org.kframework.backend.func.kst.KSTToken;
import org.kframework.backend.func.kst.KSTVariable;
import org.kframework.backend.func.kst.KSTRewrite;
import org.kframework.backend.func.kst.KSTRule;
import org.kframework.backend.func.kst.KSTSyntax;
import org.kframework.backend.func.kst.KSTModule;

import java.util.stream.Collectors;

import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public final class SortCheck {
    private int freshNum = 0;

    private SortCheck() {}

    public static String sortCheck(KSTModule m) {
        SortCheck s = new SortCheck();
        return s.sortCheckStrMod(m);
    }

    private String sortCheckStrMod(KSTModule m) {
        String stx = m.getTerms()
                      .stream()
                      .filter(x -> x instanceof KSTSyntax)
                      .map(x -> (KSTSyntax) x)
                      .map(this::sortCheckStrSyn)
                      .collect(Collectors.joining("\n", "", "\n"));

        String rls = m.getTerms()
                      .stream()
                      .filter(x -> x instanceof KSTRule)
                      .map(x -> (KSTRule) x)
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
