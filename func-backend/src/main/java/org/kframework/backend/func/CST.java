package org.kframework.backend.func;

import org.kframework.kore.K;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;

import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public class CST {
    private Optional<String> str;
    private Optional<List<CST>> list;
    private int depth;
    
    public CST(String s) {
        str = Optional.of(s);
        list = Optional.empty();
    }

    public CST(List<CST> l) {
        str = Optional.empty();
        list = Optional.of(l);
    }

    public CST(K k) {
        str = Optional.of((new KPrettyVisitor()).apply(k).render());
        list = Optional.empty();
    }
    
    public String render() {
        if(str.isPresent()) {
            return str.get();
        } else if(list.isPresent()) {
            List<String> ls = new ArrayList<>();
            for(CST c : list.get()) {
                ls.add(c.render());
            }
            return renderList(ls);
        } else {
            throw KEMException.criticalError("Failed rendering CST");
        }
    }

    private String renderList(List<String> ls) {
        if(ls.isEmpty()) {
            return "()";
        }

        if(ls.size() == 1) {
            return "(" + ls.get(0) + ")";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("(");
        sb.append(ls.get(0));
        Iterator<String> i = ls.listIterator(1);
        while(i.hasNext()) {
            sb.append(" ");
            sb.append(i.next());
        }
        sb.append(")");
        return sb.toString();
    }
}

