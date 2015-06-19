package org.kframework.backend.func;

import org.kframework.backend.func.kst.*;
import org.kframework.backend.FAST.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import scala.Tuple2;

import org.kframework.utils.errorsystem.KEMException;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public final class KSTtoFAST {
    FTarget target;

    private KSTtoFAST(FTarget tgt) {
        target = tgt;
    }

    public static FASTNode convert(FTarget tgt, KSTModule km) {
        //return new KSTtoFAST(tgt).convertModule(km);
        return null;
    }

    private FConstructor convertCon(KSTLabel label, List<KSTSort> args) {
        return null;
    }

    private FADT convertType(KSTType s) {
        //return new FADT(target);
        return null;
    }

    private List<FADT> convertModule(KSTModule km) {
        return null;
    }
}
