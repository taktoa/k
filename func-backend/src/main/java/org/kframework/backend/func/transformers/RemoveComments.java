// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.LiterateModuleComment;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;

/**
 * Delete comments
 */
public class RemoveComments extends CopyOnWriteTransformer {
    public RemoveComments(Context context) {
        super("Remove comments", context);
    }

    @Override
    public ASTNode visit(LiterateModuleComment node, Void _void)  {
        return null;
    }
}
