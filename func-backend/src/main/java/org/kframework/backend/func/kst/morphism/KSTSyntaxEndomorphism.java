package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTSyntaxEndomorphism <ST extends UnaryOperator<KSTSyntax>>
                                   implements UnaryOperator<KSTSyntax> {
    private final ST syntaxFunc;
    
    public KSTSyntaxEndomorphism(ST sf) {
        syntaxFunc = sf;
    }

    @Override
    public KSTSyntax apply(KSTSyntax ks) {
        return syntaxFunc.apply(ks);
    }
}
