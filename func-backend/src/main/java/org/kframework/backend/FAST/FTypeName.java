// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FTypeName extends FIdentifier {

    public FTypeName(FTarget target) {
        super(target.newFTypeName());
    }

}
