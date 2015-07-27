// Copyright (c) 2015 K Team. All Rights Reserved.
requires "domains.k"

module COMP-CORE-SYNTAX
  imports EMPTY-ID
  imports INT-SYNTAX
  imports BOOL-SYNTAX

  syntax AExp  ::= Int | Id
                 | "choose"
                 | AExp "/" AExp                     [left, strict]
                 > AExp "+" AExp                     [left, strict]
                 | "(" AExp ")"                      [bracket]
  syntax BExp  ::= Bool
                 | AExp "<=" AExp                    [seqstrict, latex({#1}\leq{#2})]
                 | "!" BExp                          [strict]
                 > BExp "&&" BExp                    [left, strict(1)]
                 | "(" BExp ")"                      [bracket]
  syntax Block ::= "{" "}"
                 | "{" Stmt "}"
  syntax Stmt  ::= Block
                 | "insert"
                 | "delete"
                 | Id "=" AExp ";"                   [strict(2)]
                 | "if" "(" BExp ")"
                   Block "else" Block                [strict(1)]
                 | "while" "(" BExp ")" Block
                 > Stmt Stmt                         [left]
  syntax Pgm ::= "int" Ids ";" Stmt
  syntax Ids ::= List{Id,","}
endmodule

module COMP-SYNTAX
  imports ID
  imports COMP-CORE-SYNTAX
endmodule

module COMP
  imports COMP-CORE-SYNTAX
  imports MAP
  imports INT
  syntax KResult ::= Int | Bool

  configuration <T color="yellow">
                  <k color="green"> $PGM:Pgm:K </k>
                  <state color="red"> .Map </state>
                  <set color="blue"> .Set </set>
                </T>

// Choose a random variable
  rule <k> choose => choice(Rho) ...</k> <set> Rho:Set </set>
  rule <k> delete => .K ...</k> <set>... SetItem(_) => .Set </set>
  rule <k> insert => .K ...</k> <set> _ => SetItem(1) SetItem(2) SetItem(3) SetItem(4) </set>

// AExp
  rule <k> X:Id => I ...</k> <state>... X |-> I ...</state>
  rule I1:Int / I2:Int => I1 /Int I2  when I2 =/=Int 0
  rule I1:Int + I2:Int => I1 +Int I2
// BExp
  rule I1:Int <= I2:Int => I1 <=Int I2
  rule ! T:Bool => notBool T
  rule true && B => B
  rule false && _ => false
// Block
  rule {} => .K                                      [structural]
  rule {S} => S                                      [structural]
// Stmt
  rule <k> X = I:Int; => .K ...</k> <state>... X |-> `_ => I` ...</state>
  rule S1::Stmt S2::Stmt => S1 ~> S2                 [structural]
  rule if (true)  S else _ => S
  rule if (false) _ else S => S
  rule while (B) S => if (B) {S while (B) S} else {} [structural]
// Pgm
  rule <k> int `X,Xs => Xs`;_ </k> <state> Rho:Map `.Map => X|->0` </state>
    when notBool `X in keys(Rho)`
  rule int .Ids; S => S                              [structural]
endmodule
