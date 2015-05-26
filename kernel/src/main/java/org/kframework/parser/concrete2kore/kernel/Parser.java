// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.kernel;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.definition.Production;
import org.kframework.parser.Ambiguity;
import org.kframework.parser.KList;
import org.kframework.parser.Term;
import org.kframework.parser.concrete2kore.kernel.Grammar.EntryState;
import org.kframework.parser.concrete2kore.kernel.Grammar.ExitState;
import org.kframework.parser.concrete2kore.kernel.Grammar.NextableState;
import org.kframework.parser.concrete2kore.kernel.Grammar.NonTerminal;
import org.kframework.parser.concrete2kore.kernel.Grammar.NonTerminalState;
import org.kframework.parser.concrete2kore.kernel.Grammar.PrimitiveState;
import org.kframework.parser.concrete2kore.kernel.Grammar.RegExState;
import org.kframework.parser.concrete2kore.kernel.Grammar.RuleState;
import org.kframework.parser.concrete2kore.kernel.Grammar.State;
import org.kframework.utils.algorithms.AutoVivifyingBiMap;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.pcollections.ConsPStack;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * This is the main code for running the parser.
 *
 * ----------------
 * Overview
 * ----------------
 *
 * The parser operates by maintaining tables of {@link NonTerminalCall},
 * {@link StateCall} and {@link StateReturn} records. These tables are stored
 * in ParseState and are keyed by {@link NonTerminalCall.Key},
 * {@link StateCall.Key} and {@link StateReturn.Key}. For any given Key, there is
 * a single value that can be looked up in an {@link AutoVivifyingBiMap}.
 * If no value exists for a given Key, then the {@link AutoVivifyingBiMap}
 * will create one.
 *
 * In addition to these tables, a work queue of {@link StateReturn}s
 * to be processed is kept in {@link StateReturnWorkList}.
 * This queue is ordered according to {@link StateReturn#compareTo(StateReturn)}
 * in such a way that it is impossible for a {@link StateReturn} later
 * in the queue to contribute to or influence a {@link StateReturn}
 * earlier in the queue.
 *
 * The main loop of the parser then processes elements in this queue until
 * it is empty.
 *
 * See {@link NonTerminalCall}, {@link StateCall} and {@link StateReturn}
 * (preferably in that order) for more information.
 *
 * ----------------
 * Terminology
 * ----------------
 *
 * In talking about the parser we have adopted the following naming conventions:
 *
 *  - entry/exit: the (static) start/end states of a non-terminal
 *  - call/return: the (dynamic) record for the start/end of a parse
 *  - begin/end: the start/end positions of a parse
 *  - position: a character index in a string
 *  - next/previous: next/previous edges in the state machine
 *
 * Thus we have the following terms:
 *
 *  - entry state/exit state: the first and last states in a non-terminal
 *
 *  - state call/state return: records for the entry to or exit from a state
 *  - non-terminal call: record for the entry to a non-terminal
 *
 *  - state begin/state end: the source positions for the beginning and end the span of a state
 *  - non-terminal begin: the source positions for the beginning of the span of a non-terminal
 *
 *  - next state/previous state: successor/predecessor states in a state machine
 */
public class Parser {

    /**
     * A StateCall represents the fact that the parser started parsing
     * a particular {@link State} (i.e., key.state) at a particular position
     * (i.e., key.stateBegin) while parsing a particular {@link NonTerminalCall}
     * (i.e., key.ntCall).
     *
     * For each StateCall, we keep track of the AST produced up to that point.
     * Since the AST produced may depend on the context in which the
     * {@link NonTerminalCall} associated with this StateCall
     * (i.e., key.ntCall.context), we do not simply store an AST
     * but rather a function from individual contexts.
     * This is stored in the 'function' field.
     * (See the {@link Function} class for how that is implemented).
     */
    private static class StateCall {
        /** The {@link Function} storing the AST parsed so far */
        final Function function = Function.empty();

        private static class Key implements AutoVivifyingBiMap.Create<StateCall> {
            /** The {@link NonTerminalCall} containing this StateCall */
            final NonTerminalCall ntCall;
            /** The start position of this StateCall */
            final int stateBegin;
            /** The {@link State} that this StateCall is for */
            final State state;

            private final int hashCode;

            //***************************** Start Boilerplate *****************************
            public Key(NonTerminalCall ntCall, int stateBegin, State state) {
                assert ntCall != null; assert state != null;
                this.ntCall = ntCall; this.stateBegin = stateBegin; this.state = state;
                this.hashCode = computeHash();
            }

            public StateCall create() { return new StateCall(this); }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Key key = (Key) o;

                if (stateBegin != key.stateBegin) return false;
                if (!ntCall.equals(key.ntCall)) return false;
                if (!state.equals(key.state)) return false;

                return true;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
            public int computeHash() {
                int result = ntCall.key.hashCode();
                result = 31 * result + stateBegin;
                result = 31 * result + state.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return ntCall.key.nt.name + "." + state.name + " @ "+ stateBegin;
            }
        }
        final Key key;
        StateCall(Key key) { assert key != null; this.key = key; }

        public int hashCode() {
            return this.key.hashCode();
        }
        //***************************** End Boilerplate *****************************
    }

    /**
     * A StateReturn represents the fact that the parser finished parsing
     * something that was started by a particular {@link StateCall}
     * (i.e., key.stateCall) at a particular position (i.e. key.stateEnd).
     *
     * Just was with {@link StateCall}, a StateReturn stores the AST produced up to that
     * point as the 'function' field.
     */
    private static class StateReturn implements Comparable<StateReturn> {
        /** The {@link Function} storing the AST parsed so far */
        final Function function = Function.empty();

        private final int[] orderingInfo = new int[5];

        public int compareTo(StateReturn that) {
            // The following idiom is a short-circuiting, integer "and
            // that does a lexicographic ordering over:
            //  - ntBegin (contravariently),
            //  - nt.orderingInfo (not used until we get lookaheads fixed)
            //  - stateEnd,
            //  - state.orderingInfo,
            //  - stateBegin and
            //  - state.
            // NOTE: these last two comparisons are just so we don't conflate distinct values

            int v1[] = orderingInfo;
            int v2[] = that.orderingInfo;

            int k = 1;
            int c1 = v2[0];
            int c2 = v1[0];
            if (c1 != c2) {
                return c1 - c2;
            }
            while (k < 5) {
                c1 = v1[k];
                c2 = v2[k];
                if (c1 != c2) {
                    return c1 - c2;
                }
                k++;
            }
            return 0;
        }

        private static class Key implements AutoVivifyingBiMap.Create<StateReturn> {
            /** The {@link StateCall} that this StateReturn finishes */
            public final StateCall stateCall;
            /** The end position of the parse */
            public final int stateEnd;
            private final int hashCode;
            public Key(StateCall stateCall, int stateEnd) {
                assert stateCall != null;
                //***************************** Start Boilerplate *****************************
                this.stateCall = stateCall; this.stateEnd = stateEnd;
                this.hashCode = computeHash();
            }

            public StateReturn create() { return new StateReturn(this); }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Key key = (Key) o;

                if (stateEnd != key.stateEnd) return false;
                if (!stateCall.equals(key.stateCall)) return false;

                return true;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            public int computeHash() {
                int result = stateCall.key.hashCode();
                result = 31 * result + stateEnd;
                return result;
            }

            @Override
            public String toString() {
                return stateCall.key.toString() + "-" + stateEnd;
            }
        }

        final Key key;
        StateReturn(Key key) {
            this.key = key;
            this.orderingInfo[0] = this.key.stateCall.key.ntCall.key.ntBegin;
            this.orderingInfo[1] = this.key.stateEnd;
            this.orderingInfo[2] = this.key.stateCall.key.state.orderingInfo.key;
            this.orderingInfo[3] = this.key.stateCall.key.stateBegin;
            this.orderingInfo[4] = this.key.stateCall.key.state.unique;
            //// NON-BOILERPLATE CODE: ////
            // update the NonTerminalCalls set of ExitStateReturns
            if (this.key.stateCall.key.state instanceof ExitState) {
                this.key.stateCall.key.ntCall.exitStateReturns.add(this);
            }
        }
        @Override
        public int hashCode() {
            return key.hashCode;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            StateReturn other = (StateReturn) obj;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            return true;
        }

        //***************************** End Boilerplate *****************************
    }

    /**
     * A NonTerminalCall represents the fact that the parser needs to try parsing
     * a particular {@link NonTerminal} (i.e., key.nt) starting at a particular position
     * (i.e., key.ntBegin).
     *
     * For each NonTerminalCall, we keep track of all {@link StateCall}
     * that triggered this NonTerminalCall (i.e., callers) so that when
     * the NonTerminalCall is finished, we can notify them of the successful parse.
     *
     * We also keep track of all {@link StateReturn}s for the {@link ExitState} (i.e., exitStateReturns)
     * so that when a new StateCall activates this NonTerminalCall, we can notify
     * the StateCall of successful parses of this NonTerminalCall that are
     * already computed.
     *
     * We also keep track of all {@link StateReturn}s in this NonTerminalCall that
     * should be added back on the work queue if we discover that this NonTerminalCall
     * is called from a new context (i.e., reactivations).  This is used
     * to handle context sensitivity.
     */
    private static class NonTerminalCall {
        /** The {@link StateCall}s that call this NonTerminalCall */
        final Set<StateCall> callers = new HashSet<>();
        /** The {@link StateReturn}s for the {@link ExitState} in this NonTerminalCall */
        final Set<StateReturn> exitStateReturns = new HashSet<>();
        private static class Key implements AutoVivifyingBiMap.Create<NonTerminalCall> {
            /** The {@link NonTerminal} being called */
            public final NonTerminal nt;
            /** The start position for parsing the {@link NonTerminal} */
            public final int ntBegin;
            private final int hashCode;
            //***************************** Start Boilerplate *****************************
            public Key(NonTerminal nt, int ntBegin) {
                assert nt != null;
                // assert ntBegin == c.stateBegin for c in callers
                this.nt = nt; this.ntBegin = ntBegin;
                this.hashCode = computeHash();
            }

            public NonTerminalCall create() { return new NonTerminalCall(this); }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Key key = (Key) o;

                if (ntBegin != key.ntBegin) return false;
                if (!nt.equals(key.nt)) return false;

                return true;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            public int computeHash() {
                int result = nt.hashCode();
                result = 31 * result + ntBegin;
                return result;
            }

            @Override
            public String toString() {
                return nt.name + " @ " + ntBegin;
            }
        }
        final Key key;
        NonTerminalCall(Key key) { assert key != null; this.key = key; }

        public int hashCode() {
            return this.key.hashCode();
        }
        //***************************** End Boilerplate *****************************
    }

    ////////////////

    private static class StateReturnWorkList {
        private final HashSet<StateReturn> contains = new HashSet<>();
        private final TreeSet<StateReturn> ordering = new TreeSet<>();
        public void enqueue(StateReturn stateReturn) {
            if (!contains.add(stateReturn)) return;
            ordering.add(stateReturn);
        }
        public void addAll(Set<? extends StateReturn> c) {
            if (!contains.addAll(c)) return;
            ordering.addAll(c);
        }
        public StateReturn dequeue() {
            StateReturn next = ordering.pollFirst();
            contains.remove(next);
            return next;
        }
    }

    /**
     * The state used internally by the parser.
     */
    private static class ParseState {
        // the input string which needs parsing
        final String input;
        // the reverse input used for precede restrictions
        final String reverseInput;
        // a priority queue containing the return states to be processed
        final StateReturnWorkList stateReturnWorkList = new StateReturnWorkList();
        // a preprocessed correspondence from index to line and column in the input string
        // TODO: replace lines and columns with Location class
        // TODO: extract Location class into it's own file
        final int[] lines;
        final int[] columns;
        AutoVivifyingBiMap<NonTerminalCall.Key, NonTerminalCall> ntCalls = new AutoVivifyingBiMap<>();
        AutoVivifyingBiMap<StateCall.Key, StateCall> stateCalls = new AutoVivifyingBiMap<>();
        AutoVivifyingBiMap<StateReturn.Key, StateReturn> stateReturns = new AutoVivifyingBiMap<>();

        public ParseState(String input, int startLine, int startColumn) {
            /**
             * Create arrays corresponding to the index in the input CharSequence and the line and
             * column in the text. Tab counts as one.
             *
             * The newline characters are handled according to:
             * http://www.unicode.org/standard/reports/tr13/tr13-5.html
             * http://www.unicode.org/reports/tr18/#Line_Boundaries
             */
            this.input = input;
            this.reverseInput = new StringBuilder(input).reverse().toString();
            lines = new int[input.length()+1];
            columns = new int[input.length()+1];
            int l = startLine;
            int c = startColumn;
            for (int i = 0; i < input.length(); i++) {
                lines[i] = l;
                columns[i] = c;
                switch (input.charAt(i)) {
                    case '\r' :
                        if (i+1 < input.length()) {
                            if (input.charAt(i+1) == '\n') {
                                lines[i+1] = l;
                                columns[i+1] = c + 1;
                                i++;
                            }
                        }
                    case '\n'      :
                    case  '\u000B' :
                    case  '\u000C' :
                    case  '\u0085' :
                    case  '\u2028' :
                    case  '\u2029' :
                        l++; c = 1; break;
                    default :
                        c++;
                }
            }
            lines[input.length()] = l;
            columns[input.length()] = c;
        }
    }

    ////////////////

    /**
     * A Function represents an ASTs.
     */
    private static class Function {
        /** The AST that this Function represents */
        private Set<Term> values = new HashSet<>();

        /**
         * The identity function that maps everything to a singleton containing an empty KList.
         *
         * NOTE: It is important that this function is never mutated, but we have no good way
         * of enforcing this.
         */
        static final Function IDENTITY = new Function();
        static {
            IDENTITY.values.add(KList.apply(ConsPStack.empty()));
        }

        /**
         * Returns a function that maps everything to the empty set.
         * @return The newly created function.
         */
        static Function empty() { return new Function(); }

        /**
         * A helper function that adds the mappings in 'that' to 'this' after applying 'adder' to each mapping.
         * @param that     That 'Function' from which to get the mappings to be added to 'this'.
         * @param adder    The function to be applied to each value that a particular context is mapped to.
         * @return 'true' iff new mappings were added to this
         */
        private boolean addAux(Function that, com.google.common.base.Function<Set<Term>, Set<Term>> adder) {
            return this.values.addAll(adder.apply(that.values));
        }

        /**
         * Adds all mappings in 'that' to 'this'
         * @param that    The 'Function' from which to add mappings
         * @return 'true' iff the mappings in this function changed
         */
        public boolean add(Function that) {
            return this.values.addAll(that.values);
        }

        /**
         * Add to this function the mappings resulting from composing mappings in call with the mappings in exit.
         *
         * This is used when the child (a {@link NonTerminal}) of a {@link NonTerminalState} finishes parsing.
         * In that case 'call' is the Function for the {@link StateCall} for that {@link NonTerminalState} and
         * 'exit' is the Function for the {@link StateReturn} of the {@link ExitState} in the {@link NonTerminal}.
         * @param call    The base function onto which 'exit' should be appended
         * @param exit    The function to append on 'call'
         * @return 'true' iff the mapping in this function changed
         */
        boolean addNTCall(Function call, final Function exit) {
            return addAux(call, set -> {
                Set<Term> result = new HashSet<>();
                if (!exit.values.isEmpty()) {
                    // if we found some, make an amb node and append them to the KList
                    for (Term context : set) {
                        result.add(((KList)context).add(Ambiguity.apply(exit.values)));
                    }
                }
                return result;
            });
        }

        /**
         * Add to 'this', the mappings from 'that' after they have had 'rule' applied to them.
         *
         *
         * @param that           The function on which to apply 'rule'
         * @param rule           The 'Rule' to apply to the values in 'that'
         * @param stateReturn    The StateReturn containing the StateRule containing the Rule
         * @param metaData       Metadata about the current state of parsing (e.g., location information)
         *                       that the rule can use
         * @return 'true' iff the mapping in this function changed
         */
        boolean addRule(Function that, final Rule rule, final StateReturn stateReturn, final Rule.MetaData metaData) {
            return addAux(that, set -> rule.apply(set, metaData));
        }
    }

    ////////////////

    private final ParseState s;
    private final Source source;

    public Parser(String input) {
        s = new ParseState(input, 1, 1);
        this.source = Source.apply("<unknown>");
    }

    public Parser(String input, Source source, int startLine, int startColumn) {
        s = new ParseState(input, startLine, startColumn);
        this.source = source;
    }

    /**
     * Main function to run the parser.
     * @param nt the start non-terminal
     * @param position where to start parsing in the input string
     * @return the result of parsing, as a Term
     */
    public Term parse(NonTerminal nt, int position) {
        assert nt != null : "Start symbol cannot be null.";
        activateStateCall(s.stateCalls.get(new StateCall.Key(s.ntCalls.get(
            new NonTerminalCall.Key(nt, position)), position, nt.entryState)),
            Function.IDENTITY);

        for (StateReturn stateReturn;
             (stateReturn = s.stateReturnWorkList.dequeue()) != null;) {
            this.workListStep(stateReturn);
        }

        Ambiguity result = Ambiguity.apply(new HashSet<>());
        for(StateReturn stateReturn : s.ntCalls.get(new NonTerminalCall.Key(nt,position)).exitStateReturns) {
            if (stateReturn.key.stateEnd == s.input.length()) {
                result.items().add(KList.apply(ConsPStack.singleton(Ambiguity.apply(stateReturn.function.values))));
            }
        }

        if(result.equals(Ambiguity.apply())) {
            CharSequence content = s.input;
            ParseError perror = getErrors();

            String msg = content.length() == perror.position ?
                    "Parse error: unexpected end of file." :
                    "Parse error: unexpected character '" + content.charAt(perror.position) + "'.";
            Location loc = new Location(perror.line, perror.column,
                    perror.line, perror.column + 1);
            Source source = perror.source;
            throw new ParseFailedException(new KException(
                    ExceptionType.ERROR, KExceptionGroup.INNER_PARSER, msg, source, loc));
        }

        return result;
    }

    /**
     * Looks through the list of possible parses and returns the ones that got the furthest
     * into the text.
     * @return a {@link ParseError} object containing all the possible parses that got to the
     * maximum point in the input string.
     */
    public ParseError getErrors() {
        int current = 0;
        for (StateCall.Key key : s.stateCalls.keySet()) {
            if (key.state instanceof PrimitiveState)
                current = Math.max(current, key.stateBegin);
        }
        Set<Pair<Production, RegExState>> tokens = new HashSet<>();
        for (StateCall.Key key : s.stateCalls.keySet()) {
            if (key.state instanceof RegExState && key.stateBegin == current) {
                tokens.add(new ImmutablePair<>(
                    null, ((RegExState) key.state)));
            }
        }
        return new ParseError(source, current, s.lines[current], s.columns[current], tokens);
    }

    /**
     * Contains the maximum position in the text which the parser managed to recognize.
     */
    public static class ParseError {
        /// The character offset of the error
        public final Source source;
        public final int position;
        /// The column of the error
        public final int column;
        /// The line of the error
        public final int line;
        /// Pairs of Sorts and RegEx patterns that the parsed expected to occur next
        public final Set<Pair<Production, RegExState>> tokens;

        public ParseError(Source source, int position, int line, int column, Set<Pair<Production, RegExState>> tokens) {
            assert tokens != null;
            this.source = source;
            this.position = position;
            this.tokens = tokens;
            this.column = column;
            this.line = line;
        }
    }

    private AssertionError unknownStateType() { return new AssertionError("Unknown state type"); }

    // finish the process of one state return from the work list
    private void workListStep(StateReturn stateReturn) {
        if (finishStateReturn(stateReturn)) {
            State state = stateReturn.key.stateCall.key.state;
            if (state instanceof ExitState) {
                for (StateCall stateCall : stateReturn.key.stateCall.key.ntCall.callers) {
                    s.stateReturnWorkList.enqueue(
                        s.stateReturns.get(
                            new StateReturn.Key(stateCall, stateReturn.key.stateEnd)));
                }
            } else if (state instanceof NextableState) {
                for (State nextState : ((NextableState) state).next) {
                    activateStateCall(s.stateCalls.get(new StateCall.Key(
                        stateReturn.key.stateCall.key.ntCall, stateReturn.key.stateEnd, nextState)),
                        stateReturn.function);
                }
            } else { throw unknownStateType(); }
        }
    }

    // compute the Function for a state return based on the Function for the state call associated
    // with the state return, and the type of the state
    private boolean finishStateReturn(StateReturn stateReturn) {
        if (stateReturn.key.stateCall.key.state instanceof EntryState) {
            return stateReturn.function.add(stateReturn.key.stateCall.function);
        } else if (stateReturn.key.stateCall.key.state instanceof ExitState) {
            return stateReturn.function.add(stateReturn.key.stateCall.function);
        } else if (stateReturn.key.stateCall.key.state instanceof PrimitiveState) {
            return stateReturn.function.add(stateReturn.key.stateCall.function);
        } else if (stateReturn.key.stateCall.key.state instanceof RuleState) {
            int startPosition = stateReturn.key.stateCall.key.ntCall.key.ntBegin;
            int endPosition = stateReturn.key.stateEnd;
            return stateReturn.function.addRule(stateReturn.key.stateCall.function,
                ((RuleState) stateReturn.key.stateCall.key.state).rule, stateReturn,
                new Rule.MetaData(source,
                    new Rule.MetaData.Location(startPosition, s.lines[startPosition], s.columns[startPosition]),
                    new Rule.MetaData.Location(endPosition, s.lines[endPosition], s.columns[endPosition]),
                    s.input));
        } else if (stateReturn.key.stateCall.key.state instanceof NonTerminalState) {
            return stateReturn.function.addNTCall(
                stateReturn.key.stateCall.function,
                s.stateReturns.get(new StateReturn.Key(
                    s.stateCalls.get(new StateCall.Key(
                        s.ntCalls.get(new NonTerminalCall.Key(
                            ((Grammar.NonTerminalState) stateReturn.key.stateCall.key.state).child,
                            stateReturn.key.stateCall.key.stateBegin)),
                        stateReturn.key.stateEnd,
                        ((Grammar.NonTerminalState) stateReturn.key.stateCall.key.state).child.exitState)),
                    stateReturn.key.stateEnd)).function);
        } else { throw unknownStateType(); }
    }

    // copy Function from state return to next state call
    // also put state return in the queue if need be
    private void activateStateCall(StateCall stateCall, Function function) {
        if (!stateCall.function.add(function)) { return; }
        State nextState = stateCall.key.state;
        // These types of states
        if (nextState instanceof EntryState ||
            nextState instanceof ExitState ||
            nextState instanceof RuleState) {
            s.stateReturnWorkList.enqueue(
                s.stateReturns.get(
                    new StateReturn.Key(stateCall, stateCall.key.stateBegin)));
        } else if (nextState instanceof PrimitiveState) {
            for (PrimitiveState.MatchResult matchResult :
                    ((PrimitiveState)nextState).matches(s.input, s.reverseInput, stateCall.key.stateBegin)) {
                s.stateReturnWorkList.enqueue(
                    s.stateReturns.get(
                        new StateReturn.Key(stateCall, matchResult.matchEnd)));
            }
        // not instanceof SimpleState
        } else if (nextState instanceof NonTerminalState) {
            // add to the ntCall
            NonTerminalCall ntCall = s.ntCalls.get(new NonTerminalCall.Key(
                ((NonTerminalState) nextState).child, stateCall.key.stateBegin));
            ntCall.callers.add(stateCall);
            // activate the entry state call (almost like activateStateCall but we have no stateReturn)
            StateCall entryStateCall = s.stateCalls.get(new StateCall.Key(
                ntCall, stateCall.key.stateBegin, ntCall.key.nt.entryState));
            activateStateCall(entryStateCall, Function.IDENTITY);
            // process existStateReturns already done in the ntCall
            for (StateReturn exitStateReturn : ntCall.exitStateReturns) {
                s.stateReturnWorkList.enqueue(
                    s.stateReturns.get(
                        new StateReturn.Key(stateCall, exitStateReturn.key.stateEnd)));
            }
        } else { throw unknownStateType(); }
    }
}
