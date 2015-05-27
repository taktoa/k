// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;

/**
 * Debug transformer
 */
public class DebugTransformer extends CopyOnWriteTransformer {
    public DebugTransformer(Context context) {
        super("Remove comments", context);
    }

    @Override
    public ASTNode visit(ASTNode node, Void _void)  {
        System.out.print("Node: ");
        System.out.println(node.toString());
        System.out.print("Node class: ");
        System.out.println(node.getClass().toString());
        return node;
    }
}
