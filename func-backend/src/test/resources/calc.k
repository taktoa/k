// Copyright (c) 2014-2015 K Team. All Rights Reserved.

module CALC-SYNTAX
  syntax Exp ::= Int
  syntax Exp ::= Exp "+" Exp [klabel('_+_), strict, left]
  syntax Exp ::= "(" Exp ")" [bracket]
  syntax Pgm ::= Exp [klabel('int_;_)]
  syntax KResult ::= Int
endmodule

module CALC
  import CALC-SYNTAX

  configuration
    <top>
      <k> $PGM:Pgm:K </k>
    </top>

  rule A + B => A +Int B requires isInt(A) andBool isInt(B) ensures KTrue
endmodule
