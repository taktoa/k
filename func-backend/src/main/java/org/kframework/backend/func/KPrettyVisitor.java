package org.kframework.backend.func;

import org.kframework.kore.InjectedKLabel;
import org.kframework.attributes.Att;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.KLabel;
import org.kframework.kore.AbstractKORETransformer;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public class KPrettyVisitor extends AbstractKORETransformer<CST> {
    @Override
    public CST apply(KApply k) {
        List<CST> l = new ArrayList<>();
        l.add(apply(k.klabel()));
        for(K i : k.klist().items()) {
            l.add(apply(i));
        }
        return new CST(l);
    }

    @Override
    public CST apply(KRewrite k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST("=>"));
        l.add(apply(k.left()));
        l.add(apply(k.right()));
        l.add(apply(k.att()));
        return new CST(l);
    }

    @Override
    public CST apply(KToken k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST("token"));
        l.add(new CST(k.s()));
        l.add(new CST(k.sort().toString()));
        return new CST(l);
    }

    @Override
    public CST apply(KVariable k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST(k.toString()));
        return new CST(l);
    }

    @Override
    public CST apply(KSequence k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST("quote"));
        for(K i : k.items()) {
            l.add(apply(i));
        }
        return new CST(l);
    }

    @Override
    public CST apply(InjectedKLabel k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST("quote"));
        l.add(new CST(k.toString()));
        return new CST(l);
    }

    public CST apply(Att k) {
        List<CST> l = new ArrayList<>();
        l.add(new CST("att"));
        Map<String, KApply> am = scalaMapAsJava(k.attMap());
        for(String key : am.keySet()) {
            l.add(apply(am.get(key)));
        }
        return new CST(l);
    }

    public CST apply(KLabel k) {
        return new CST(k.name());
    }
}
