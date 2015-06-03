package org.kframework.backend.func;

/**
 * Created by Remy Goldschmidt on 06/02/2015
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
