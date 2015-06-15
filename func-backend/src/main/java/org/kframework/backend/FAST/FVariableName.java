// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.FAST;

/**
 * @author: Sebastian Conybeare
 */
public class FVariableName extends FIdentifier {

    public FVariableName(FTarget target) {
        super(target.newFVariable());
    }

}
