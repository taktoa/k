package org.kframework.backend.func.kst;

import java.util.function.Function;
import java.util.Optional;

// saving this as a backup

public class KSTSyntaxEndomorphism implements Function<KSTSyntax, KSTSyntax> {
    private KSTSyntaxEndomorphism syntaxEndo;
    private KSTRuleEndomorphism   ruleEndo;
    private Optional<KSTModuleEndomorphism> next;

    public KSTModuleEndomorphism(KSTSyntaxEndomorphism kse,
                                 KSTRuleEndomorphism kre) {
        KSTModuleEndomorphism(kse, kre, Optional.empty());
    }

    private KSTModuleEndomorphism(KSTSyntaxEndomorphism kse,
                                  KSTRuleEndomorphism kre,
                                  KSTModuleEndomorphism nxt) {
        KSTModuleEndomorphism(kse, kre, Optional.of(nxt));
    }

    private KSTModuleEndomorphism(KSTSyntaxEndomorphism kse,
                                  KSTRuleEndomorphism kre,
                                  Optional<KSTModuleEndomorphism> nxt) {
        syntaxEndo = kse;
        ruleEndo = kre;
        next = nxt;
    }

    @Override
    public KSTModule apply(KSTModule km) {
        Set<KSTSyntax> newStx = km.getSyntax()
                                  .stream()
                                  .map(syntaxEndo::apply)
                                  .collect(Collectors.toSet());
        Set<KSTRule>   newRls = km.getRules()
                                  .stream()
                                  .map(ruleEndo::apply)
                                  .collect(Collectors.toSet());
        KSTModule res = new KSTModule(newStx, newRls);

        return next.map(x -> x::apply(res)).orElse(res);
    }

    @Override
    public static KSTModuleEndomorphism compose(KSTModuleEndomorphism e) {
        KSTModuleEndomorphism res = e.deepCopy();
        res.setNext(this);
        return res;
    }

    @Override
    public static KSTModuleEndomorphism andThen(KSTModuleEndomorphism e) {
        KSTModuleEndomorphism res = this.deepCopy();
        res.setNext(e);
        return res;
    }

    @Override
    public static KSTModuleEndomorphism identity() {
        return new KSTModuleEndomorphism(KSTSyntaxEndomorphism.identity(),
                                         KSTRuleEndomorphism.identity());
    }
    
    private static KSTModuleEndomorphism deepCopy() {
        return new KSTModuleEndomorphism(syntaxEndo, ruleEndo, next);
    }
    
    private void setNext(KSTModuleEndomorphism n) {
        next = Optional.of(n);
    }
}
