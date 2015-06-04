package org.kframework.backend.func;

/**
 * @author: Remy Goldschmidt
 */
public class SyntaxBuilder {
    private StringBuilder sb;

    public SyntaxBuilder(StringBuilder s) {
        sb = s;
    }
    
    public void append(String a) {
        sb.append(a);
    }
    
    public String render() {
        return sb.toString();
    }
}
