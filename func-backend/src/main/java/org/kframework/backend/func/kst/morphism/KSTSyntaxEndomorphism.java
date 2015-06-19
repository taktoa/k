package org.kframework.backend.func.kst;

import java.util.function.UnaryOperator;

public class KSTSyntaxEndomorphism implements UnaryOperator<KSTSyntax> {
    private final UnaryOperator<KSTSyntax> syntaxFunc;

    public KSTSyntaxEndomorphism(UnaryOperator<KSTSyntax> sf) {
        syntaxFunc = sf;
    }

    @Override
    public KSTSyntax apply(KSTSyntax ks) {
        return syntaxFunc.apply(ks);
    }
}
