// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.compile;

import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.kil.*;
import org.kframework.kil.Cell.Ellipses;
import org.kframework.kil.Cell.Multiplicity;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;

public class ConfigurationCleaner extends CopyOnWriteTransformer {

    public ConfigurationCleaner(Context context) {
        super("Configuration Cleaner", context);
    }

    private KEMException expectedCellError(Cell node) {
        String errorStr = "Expecting Cell, but got "
                        + node.getClass()
                        + " in Configuration Cleaner.";
        return KExceptionManager.internalError(errorStr, this, node);
    }
    
    @Override
    public ASTNode visit(Cell node, Void _void)  {
        if (   node.getMultiplicity() == Multiplicity.ANY
            || node.getMultiplicity() == Multiplicity.MAYBE) {
            if (node.variables().isEmpty()) {
                return new Bag();
            }
        }

        ASTNode result = super.visit(node, _void);
        if (result == null) { return null; }

        if (result == node) {
            node = node.shallowCopy();
        } else {
            if (!(result instanceof Cell)) {
                throw expectedCellError(node);
            } else {
                node = (Cell) result;
            }
        }
        node.setDefaultAttributes();
        node.setEllipses(Ellipses.NONE);
        return node;
    }

}


