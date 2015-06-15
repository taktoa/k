// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FConstructorName extends FIdentifier {

    public FConstructorName(FTarget target) {
        super(target.newFConstructorName());
    }

}
