// Copyright (c) 2015 K Team. All Rights Reserved.

// Module defining only the sorts K and KString, useful for modularity
module BASIC-K
  syntax K
  syntax KString ::= r"[\\\"](([^\\\"\n\r\\\\])|([\\\\][nrtf\\\"\\\\])|([\\\\][x][0-9a-fa-f]{2})|([\\\\][u][0-9a-fa-f]{4})|([\\\\][u][0-9a-fa-f]{8}))*[\\\"]"      [token, hook(org.kframework.kore.KString)]
    // optionally qualified strings, like in Scala "abc", i"abc", r"a*bc", etc.
  syntax KLabel
endmodule


module KAST
  imports BASIC-K
  syntax KItem
  syntax K     ::= KItem
  syntax KBott ::= "#token" "(" KString "," KString ")"  [klabel(#KToken), hook(org.kframework.kore.KToken)]
                 | "#klabel" "(" KLabel ")"              [klabel(#WrappedKLabel), hook(org.kframework.kore.meta.WrappedKLabel)]
                 | KLabel "(" KList ")"                  [klabel(#KApply), hook(org.kframework.kore.KApply)]
  syntax KItem ::= KBott

  syntax KLabel ::= r"`(\\\\`|\\\\\\\\|[^`\\\\\n\r])+`" [token, hook(org.kframework.kore.KLabel)]
                  | r"(?<![a-zA-Z0-9])[#a-z][a-zA-Z0-9]*"               [token, hook(org.kframework.kore.KLabel), autoReject]
                       // something that doesn't collide with meta-variables

  syntax KList ::= K
                 | ".KList"          [klabel(#EmptyKList), hook(org.kframework.kore.EmptyKList)]
                 | ".::KList"        [klabel(#EmptyKList), hook(org.kframework.kore.EmptyKList)]
                 | KList "," KList   [klabel(#KList), left, assoc, unit(#EmptyKList), hook(org.kframework.kore.KList), prefer]
                 | KList ",," KList  [klabel(#KList), left, assoc, unit(#EmptyKList), hook(org.kframework.kore.KList), prefer]
endmodule


// To be used when parsing/pretty-printing ground configurations
module KSEQ
  imports KAST
  imports K-TOP-SORT
  syntax KBott ::= ".K"      [klabel(#EmptyK), hook(org.kframework.kore.EmptyK)]
                 | ".::K"    [klabel(#EmptyK), hook(org.kframework.kore.EmptyK)]
                 | K "~>" K  [klabel(#KSequence), left, assoc, unit(#EmptyK), hook(org.kframework.kore.KSequence)]
  syntax left #KSequence
  syntax KBott     ::= "`" K "`"    [bracket]
  syntax KConfigVar
endmodule


// To be used when parsing/pretty-printing symbolic configurations
module KSEQ-SYMBOLIC
  imports KSEQ
  syntax #KVariable  ::= r"(?<![A-Za-z0-9_\\$!\\?])(\\!|\\?)?([A-Z][A-Za-z0-9'_]*|_)"   [token, autoReject, hook(org.kframework.kore.KVariable)]
  syntax KConfigVar ::= r"(?<![A-Za-z0-9_\\$!\\?])(\\$)([A-Z][A-Za-z0-9'_]*)"          [token, autoReject]
  syntax KBott      ::= #KVariable
  syntax KBott      ::= KConfigVar
  syntax KLabel     ::= #KVariable
endmodule

module KCELLS
  imports KAST

  syntax Cell
  syntax Bag ::= Bag Bag  [left, assoc, klabel(#cells), unit(#cells)]
               | ".Bag"   [klabel(#cells)]
               | ".::Bag" [klabel(#cells)]
               | Cell
  syntax K ::= Bag
  syntax Bag ::= KBott
endmodule

module RULE-CELLS
  imports KCELLS
  // if this module is imported, the parser automatically
  // generates, for all productions that have the attribute 'cell' or 'maincell',
  // a production like below:
  //syntax Cell ::= "<top>" #OptionalDots K #OptionalDots "</top>" [klabel(<top>)]

  syntax #OptionalDots ::= "..." [klabel(#dots)]
                         | ""    [klabel(#noDots)]
endmodule

module CONFIG-CELLS
  imports KCELLS
  syntax #CellName ::= r"[a-zA-Z0-9\\-]+"  [token]
  syntax Cell ::= "<" #CellName #CellProperties ">" K "</" #CellName ">" [klabel(#configCell)]
  syntax #CellProperties ::= #CellProperty #CellProperties [klabel(#cellPropertyList)]
                           | ""                            [klabel(#cellPropertyListTerminator)]
  syntax #CellProperty ::= #CellName "=" KString           [klabel(#cellProperty)]

endmodule


module REQUIRES-ENSURES
  imports BASIC-K

  syntax RuleContent ::= K                                 [klabel("#ruleNoConditions")]
                       | K "requires" K                    [klabel("#ruleRequires")]
                       | K "when" K                        [klabel("#ruleRequires")]
                       | K "ensures"  K                    [klabel("#ruleEnsures")]
                       | K "requires" K "ensures" K        [klabel("#ruleRequiresEnsures")]
                       | K "when" K "ensures" K            [klabel("#ruleRequiresEnsures")]
endmodule

module K-TOP-SORT
  // if this module is imported, the parser automatically
  // generates, for all sorts, productions of the form:
  // K     ::= Sort
  // this is part of the mechanism that allows concrete user syntax in K
endmodule

module K-BOTTOM-SORT
  // if this module is imported, the parser automatically
  // generates, for all sorts, productions of the form:
  // Sort  ::= KBott
  // this is part of the mechanism that allows concrete user syntax in K
endmodule

module K-SORT-LATTICE
  imports K-TOP-SORT
  imports K-BOTTOM-SORT
endmodule

module AUTO-CASTS
  // if this module is imported, the parser automatically
  // generates, for all sorts, productions of the form:
  // Sort  ::= Sort "::Sort"
  // Sort  ::= Sort ":Sort"
  // KBott ::= Sort "<:Sort"
  // Sort  ::= K    ":>Sort"
  // this is part of the mechanism that allows concrete user syntax in K
endmodule

module AUTO-FOLLOW
  // if this module is imported, the parser automatically
  // generates a follow restriction for every terminal which is a prefix
  // of another terminal. This is useful to prevent ambiguities such as:
  // syntax K ::= "a"
  // syntax K ::= "b"
  // syntax K ::= "ab"
  // syntax K ::= K K
  // #parse("ab", "K")
  // In the above example, the terminal "a" is not allowed to be followed by a "b"
  // because it would turn the terminal into the terminal "ab".
endmodule

module DEFAULT-CONFIGURATION
  imports BASIC-K

  configuration <k> $PGM:K </k>
endmodule

// To be used to parse semantic rules
module K
  imports KSEQ-SYMBOLIC
  imports REQUIRES-ENSURES
  imports K-SORT-LATTICE
  imports AUTO-CASTS
  imports AUTO-FOLLOW
  syntax KBott     ::= K "=>" K     [klabel(#KRewrite), hook(org.kframework.kore.KRewrite), non-assoc]
  syntax non-assoc #KRewrite

endmodule
