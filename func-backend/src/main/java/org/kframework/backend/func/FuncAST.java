package org.kframework.backend.func;

/**
 * @author: Remy Goldschmidt
 */
public class FuncAST {
    private String ast;
    public FuncAST(String s) {
        ast = s;
    }
    public String render() {
        return ast;
    }
}
