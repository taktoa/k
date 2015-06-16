package org.kframework.backend.func.kst;

import java.util.function.Function;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class RulesToFunctions implements UnaryOperator<KSTModule> {
    public static UnaryOperator<KSTModule> getModuleEndo() {
        return (UnaryOperator<KSTModule>) new RulesToFunctions();
    }

    @Override
    public KSTModule apply(KSTModule k) {
        Set<KSTLabel> fls = getFunctionLabels(k);
        System.out.println("DBG: " + fls.toString());
        return new KSTModule(k.getTerms()
                              .stream()
                              .map(x -> moduleTermTransformer(x, fls))
                              .collect(Collectors.toSet()));
    }

    private static Set<KSTLabel> getFunctionLabels(KSTModule k) {
        return k.getTerms()
                .stream()
                .filter(x -> isFunction(x.getAtts()))
                .filter(x -> x instanceof KSTModuleDefinition)
                .map(x -> ((KSTModuleDefinition) x).getLabel())
                .collect(Collectors.toSet());
    }

    private static KSTModuleTerm moduleTermTransformer(KSTModuleTerm kmt,
                                                       Set<KSTLabel> fls) {
//        if(! isFunctionRule(kmt)) {
//            return ruleToFunction((KSTRule) kmt, fls);
//        } else {
//            return kmt;
//        }
        if(! (kmt instanceof KSTRule)) {
            return kmt;
        }

        //        System.out.println("DBG: ISRULE: " + kmt.toString());
        return ruleToFunction((KSTRule) kmt, fls, kmt);
    }

    private static KSTModuleTerm ruleToFunction(KSTRule kr,
                                                Set<KSTLabel> fls,
                                                KSTModuleTerm fail) {
        KSTTerm body = kr.getBody();

        if(! (body instanceof KSTRewrite)) {
            return fail;
        }

        return rewriteToFunction((KSTRewrite) body, fls, fail);
    }

    private static KSTModuleTerm rewriteToFunction(KSTRewrite krw,
                                                   Set<KSTLabel> fls,
                                                   KSTModuleTerm fail) {
        KSTTerm left  = krw.getLHS();
        KSTTerm right = krw.getRHS();

        if(! (left instanceof KSTApply)) {
            return fail;
        }

        KSTApply leftApp = (KSTApply) left;
        KSTLabel funcLabel = leftApp.getLabel();

        if(! fls.contains(funcLabel)) {
            return fail;
        }

        System.out.println("DBG: " + funcLabel + " is a function label");
        KSTModuleTerm test = applyToFunction(leftApp, right, funcLabel, fail);
        System.out.println("DBG: Result: " + test);
        return test;
    }

    private static KSTModuleTerm applyToFunction(KSTApply leftApp,
                                                KSTTerm funcBody,
                                                KSTLabel funcLabel,
                                                KSTModuleTerm fail) {
        List<KSTTerm> funcArgs = new ArrayList<>(leftApp.getArgs().size());

        for(KSTTerm t : leftApp.getArgs()) {
            if(! checkIfValidPattern(t)) {
                return fail;
            }

            funcArgs.add(t);
        }

        return (KSTModuleTerm) new KSTFunction(funcLabel,
                                               leftApp.getSort(),
                                               funcArgs,
                                               funcBody);
    }

    private static boolean isFunctionRule(KSTModuleTerm kmt) {
        if(kmt instanceof KSTRule) {
            return isFunction(kmt.getAtts()) ? hasNoSideConditions((KSTRule) kmt)
                                             : false;
        }
        return false;
    }

    private static boolean checkIfValidPattern(KSTTerm t) {
        return true; // FIXME
    }

    private static boolean hasNoSideConditions(KSTRule kr) {
        boolean rq = kr.getRequires() instanceof KSTToken; // a little dodgy
        boolean en = kr.getEnsures()  instanceof KSTToken; // ditto
        return rq && en;
    }

    private static boolean isFunction(Set<KSTAtt> atts) {
        return !atts.stream()
                    .filter(RulesToFunctions::isFunction)
                    .collect(Collectors.toSet())
                    .isEmpty();
    }

    private static Boolean isFunction(KSTAtt att) {
        boolean test = "function".equals(att.getLabel().getName());
//        if(test) {
//            System.out.format("DBG: function == %s\n", att);
//        } else {
//            System.out.format("DBG: function =/= %s\n", att.getLabel().getName());
//        }
        return Boolean.valueOf(test);
    }
}
