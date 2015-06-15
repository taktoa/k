package org.kframework.backend.FAST;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FASTExampleADTTest {
    @Test
    public void testUnit() {
        String correctOutput = "data Nat = Z | S Nat"; // FIXME(sebmathguy): probably wrong, given munging
        FTarget tgt = new HaskellFTarget(); // FIXME(sebmathguy): cannot yet be instantiated
        FADT nat;
        FConstructorSignature conSigZ, conSigS;
        FArgumentSignature argSigZ = new FArgumentSignature(ImmutableList.of());
        FArgumentSignature argSigS = new FArgumentSignature(ImmutableList.of(nat));
        nat = new FADT(ImmutableList.of(argSigZ, argSigS), tgt);
        nat.declare(); // FIXME(sebmathguy): line fails to compile because declare() doesn't exist yet
        String generatedOutput = tgt.getDeclarations(); // FIXME(sebmathguy): same as above

        assertEquals(correctOutput, generatedOutput);
    }
}
