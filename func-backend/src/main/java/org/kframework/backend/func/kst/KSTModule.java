package org.kframework.backend.func.kst;

import java.util.Set;

public class KSTModule {
    private final Set<KSTSyntax> syntax;
    private final Set<KSTRule> rules;

    public KSTModule(Set<KSTSyntax> s, Set<KSTRule> r) {
        syntax = s;
        rules = r;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("module:");
        for(KSTSyntax s : syntax) {
            sb.append("\n");
            sb.append(s);
        }
        for(KSTRule r : rules) {
            sb.append("\n");
            sb.append(r);
        }
        sb.append("\n");
        return sb.toString();
    }
}
