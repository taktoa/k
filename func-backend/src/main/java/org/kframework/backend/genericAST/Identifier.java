// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.genericAST;

/**
 * @author: Sebastian Conybeare
 */
public class Identifier {
    
    private final String name;
    
    protected Identifier(String name) {
        this.name = name;
    }
    
    public String toString() {
        return name;
    }
    
}
