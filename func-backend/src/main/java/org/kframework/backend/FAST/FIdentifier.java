// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FIdentifier {
    
    private final String name;
    
    protected FIdentifier(String name) {
        this.name = name;
    }
    
    public String toString() {
        return name;
    }
    
}
