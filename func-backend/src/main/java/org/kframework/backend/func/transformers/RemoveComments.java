// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Bracket;
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

//    @Override
//    public ASTNode visit(ASTNode node, Void _void)  {
//        System.out.print("NODE TYPE: ");
//        System.out.println(node.getClass().toString());
//        return node;
//    }

//    @Override
//    public ASTNode visit(Bracket node, Void _void)  {
//        System.out.println("Remove: " + node.getFilename() + ":" + node.getLocation());
//        return this.visitNode(node.getContent());
//    }
// 
//    @Override
//    public ASTNode visit(TermCons node, Void _void)  {
//        System.out.println("Remove: " + node.getFilename() + ":" + node.getLocation());
//        if (node.getProduction().containsAttribute("bracket"))
//            return this.visitNode(node.getContents().get(0));
//        return super.visit(node, _void);
//    }
}
