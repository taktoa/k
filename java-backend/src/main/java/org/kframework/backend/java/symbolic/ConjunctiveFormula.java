// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.kil.Bottom;
import org.kframework.backend.java.kil.BuiltinMap;
import org.kframework.backend.java.kil.CollectionInternalRepresentation;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabel;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.Kind;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Token;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.RewriteEngineUtils;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A conjunction of equalities (between terms with variables) and disjunctions
 *
 * @see org.kframework.backend.java.symbolic.Equality
 * @see org.kframework.backend.java.symbolic.DisjunctiveFormula
 */
public class ConjunctiveFormula extends Term implements CollectionInternalRepresentation {

    public static final String SEPARATOR = " /\\ ";

    public static ConjunctiveFormula of(TermContext context) {
        return new ConjunctiveFormula(
                Substitution.empty(),
                PersistentUniqueList.empty(),
                PersistentUniqueList.empty(),
                TruthValue.TRUE,
                context);
    }

    public static ConjunctiveFormula of(ConjunctiveFormula formula) {
        return new ConjunctiveFormula(
                formula.substitution,
                formula.equalities,
                formula.disjunctions,
                formula.truthValue,
                formula.falsifyingEquality,
                formula.context);
    }

    public static ConjunctiveFormula of(Substitution<Variable, Term> substitution, TermContext context) {
        return new ConjunctiveFormula(
                substitution,
                PersistentUniqueList.empty(),
                PersistentUniqueList.empty(),
                substitution.isEmpty() ? TruthValue.TRUE : TruthValue.UNKNOWN,
                context);
    }

    public static ConjunctiveFormula of(
            Substitution<Variable, Term> substitution,
            PersistentUniqueList<Equality> equalities,
            PersistentUniqueList<DisjunctiveFormula> disjunctions,
            TermContext context) {
        return new ConjunctiveFormula(
                substitution,
                equalities,
                disjunctions,
                substitution.isEmpty() && equalities.isEmpty() && disjunctions.isEmpty() ? TruthValue.TRUE : TruthValue.UNKNOWN,
                context);
    }

    private final Substitution<Variable, Term> substitution;
    private final PersistentUniqueList<Equality> equalities;
    private final PersistentUniqueList<DisjunctiveFormula> disjunctions;

    private final TruthValue truthValue;

    private final Equality falsifyingEquality;

    private transient final TermContext context;

    public ConjunctiveFormula(
            Substitution<Variable, Term> substitution,
            PersistentUniqueList<Equality> equalities,
            PersistentUniqueList<DisjunctiveFormula> disjunctions,
            TruthValue truthValue,
            Equality falsifyingEquality,
            TermContext context) {
        super(Kind.KITEM);

        this.substitution = substitution;
        this.equalities = equalities;
        this.disjunctions = disjunctions;
        this.truthValue = truthValue;
        this.falsifyingEquality = falsifyingEquality;
        this.context = context;
    }

    public ConjunctiveFormula(
            Substitution<Variable, Term> substitution,
            PersistentUniqueList<Equality> equalities,
            PersistentUniqueList<DisjunctiveFormula> disjunctions,
            TruthValue truthValue,
            TermContext context) {
        this(substitution, equalities, disjunctions, truthValue, null, context);
    }

    public Substitution<Variable, Term> substitution() {
        return substitution;
    }

    public PersistentUniqueList<Equality> equalities() {
        return equalities;
    }

    public PersistentUniqueList<DisjunctiveFormula> disjunctions() {
        return disjunctions;
    }

    public TermContext termContext() {
        return context;
    }

    /**
     * Adds the side condition of a rule to this constraint. The side condition is represented
     * as a list of {@code Term}s that are expected to be equal to {@code BoolToken#TRUE}.
     */
    public ConjunctiveFormula addAll(List<Term> condition) {
        ConjunctiveFormula result = this;
        for (Term term : condition) {
            result = result.add(term, BoolToken.TRUE);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    public ConjunctiveFormula addAndSimplify(Object term) {
        return add(term).simplify();
    }

    public ConjunctiveFormula add(ConjunctiveFormula conjunction) {
        return add(conjunction.substitution)
                .addAll(conjunction.equalities)
                .addAll(conjunction.disjunctions);
    }

    public ConjunctiveFormula add(Substitution<Variable, Term> substitution) {
        return addAll(substitution.equalities(context));
    }

    public ConjunctiveFormula add(Equality equality) {
        /* simplify andBool */
        if (equality.leftHandSide() instanceof KItem
                && ((KItem) equality.leftHandSide()).kLabel().toString().equals("'_andBool_")
                && equality.rightHandSide().equals(BoolToken.TRUE)) {
            return this
                    .add(((KList) ((KItem) equality.leftHandSide()).kList()).get(0), BoolToken.TRUE)
                    .add(((KList) ((KItem) equality.leftHandSide()).kList()).get(1), BoolToken.TRUE);
        }
        if (equality.rightHandSide() instanceof KItem
                && ((KItem) equality.rightHandSide()).kLabel().toString().equals("'_andBool_")
                && equality.leftHandSide().equals(BoolToken.TRUE)) {
            return this
                    .add(((KList) ((KItem) equality.rightHandSide()).kList()).get(0), BoolToken.TRUE)
                    .add(((KList) ((KItem) equality.rightHandSide()).kList()).get(1), BoolToken.TRUE);
        }

        /* simplify orBool */
        if (equality.leftHandSide() instanceof KItem
                && ((KItem) equality.leftHandSide()).kLabel().toString().equals("'_orBool_")
                && equality.rightHandSide().equals(BoolToken.FALSE)) {
            return this
                    .add(((KList) ((KItem) equality.leftHandSide()).kList()).get(0), BoolToken.FALSE)
                    .add(((KList) ((KItem) equality.leftHandSide()).kList()).get(1), BoolToken.FALSE);
        }
        if (equality.rightHandSide() instanceof KItem
                && ((KItem) equality.rightHandSide()).kLabel().toString().equals("'_orBool_")
                && equality.leftHandSide().equals(BoolToken.FALSE)) {
            return this
                    .add(((KList) ((KItem) equality.rightHandSide()).kList()).get(0), BoolToken.FALSE)
                    .add(((KList) ((KItem) equality.rightHandSide()).kList()).get(1), BoolToken.FALSE);
        }

        /* simplify notBool */
        if (equality.leftHandSide() instanceof KItem
                && ((KItem) equality.leftHandSide()).kLabel().toString().equals("'notBool_")
                && equality.rightHandSide() instanceof BoolToken) {
            return this.add(
                    ((KList) ((KItem) equality.leftHandSide()).kList()).get(0),
                    BoolToken.of(!((BoolToken) equality.rightHandSide()).booleanValue()));
        }
        if (equality.rightHandSide() instanceof KItem
                && ((KItem) equality.rightHandSide()).kLabel().toString().equals("'notBool_")
                && equality.leftHandSide() instanceof BoolToken) {
            return this.add(
                    ((KList) ((KItem) equality.rightHandSide()).kList()).get(0),
                    BoolToken.of(!((BoolToken) equality.leftHandSide()).booleanValue()));
        }

        return new ConjunctiveFormula(
                substitution,
                equalities.plus(equality),
                disjunctions,
                truthValue != TruthValue.FALSE ? TruthValue.UNKNOWN : TruthValue.FALSE,
                falsifyingEquality,
                context);
    }

    public ConjunctiveFormula unsafeAddVariableBinding(Variable variable, Term term) {
        // these assertions are commented out for performance reasons
        //assert term.substituteAndEvaluate(substitution, context) == term;
        //assert !term.variableSet().contains(variable);
        Term previousTerm = substitution.get(variable);
        if (previousTerm == null) {
            return new ConjunctiveFormula(
                    substitution.plus(variable, term),
                    equalities,
                    disjunctions,
                    truthValue,
                    context);
        } else if (previousTerm.equals(term)) {
            return this;
        } else {
            return falsify(substitution, equalities, disjunctions, new Equality(previousTerm, term, context));
        }
    }

    public ConjunctiveFormula add(Term leftHandSide, Term rightHandSide) {
        return add(new Equality(leftHandSide, rightHandSide, context));
    }

    public ConjunctiveFormula add(DisjunctiveFormula disjunction) {
        return new ConjunctiveFormula(
                substitution,
                equalities,
                disjunctions.plus(disjunction),
                truthValue != TruthValue.FALSE ? TruthValue.UNKNOWN : TruthValue.FALSE,
                falsifyingEquality,
                context);
    }

    @SuppressWarnings("unchecked")
    public ConjunctiveFormula add(Object term) {
        if (term instanceof ConjunctiveFormula) {
            return add((ConjunctiveFormula) term);
        } else if (term instanceof Substitution) {
            return add((Substitution) term);
        } else if (term instanceof Equality) {
            return add((Equality) term);
        } else if (term instanceof DisjunctiveFormula) {
            return add((DisjunctiveFormula) term);
        } else {
            assert false : "invalid argument found: " + term;
            return null;
        }
    }

    public ConjunctiveFormula addAll(Iterable<? extends Object> terms) {
        ConjunctiveFormula result = this;
        for (Object term : terms) {
            result = result.add(term);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    public TruthValue truthValue() {
        return truthValue;
    }

    public boolean isTrue() {
        return truthValue == TruthValue.TRUE;
    }

    public boolean isFalse() {
        return truthValue == TruthValue.FALSE;
    }

    public boolean isUnknown() {
        return truthValue == TruthValue.UNKNOWN;
    }

    /**
     * Removes specified variable bindings from this constraint.
     * <p>
     * Note: this method should only be used to garbage collect useless
     * bindings. It is called to remove all bindings of the rewrite rule
     * variables after building the rewrite result.
     */
    public ConjunctiveFormula removeBindings(Set<Variable> variablesToRemove) {
        return ConjunctiveFormula.of(
                substitution.minusAll(variablesToRemove),
                equalities,
                disjunctions,
                context);
    }

    /**
     * Simplifies this conjunctive formula as much as possible.
     * Decomposes equalities by using unification.
     */
    public ConjunctiveFormula simplify() {
        return simplify(false, true);
    }

    /**
     * Similar to {@link ConjunctiveFormula#simplify()} except that equalities
     * between builtin data structures will remain intact if they cannot be
     * resolved completely.
     */
    public ConjunctiveFormula simplifyBeforePatternFolding() {
        return simplify(false, false);
    }

    public ConjunctiveFormula simplifyModuloPatternFolding() {
        return simplify(true, true);
    }

    /**
     * Simplifies this conjunctive formula as much as possible.
     * Decomposes equalities by using unification.
     */
    public ConjunctiveFormula simplify(boolean patternFolding, boolean partialSimplification) {
        assert !isFalse();
        Substitution<Variable, Term> substitution = this.substitution;
        PersistentUniqueList<Equality> equalities = this.equalities;
        PersistentUniqueList<DisjunctiveFormula> disjunctions = this.disjunctions;

        boolean change;
        do {
            change = false;
            PersistentUniqueList<Equality> pendingEqualities = PersistentUniqueList.empty();
            for (int i = 0; i < equalities.size(); ++i) {
                Equality equality = equalities.get(i);
                Term leftHandSide = equality.leftHandSide().substituteAndEvaluate(substitution, context);
                Term rightHandSide = equality.rightHandSide().substituteAndEvaluate(substitution, context);
                equality = new Equality(leftHandSide, rightHandSide, context);
                if (equality.isTrue()) {
                    // delete
                } else if (equality.truthValue() == TruthValue.FALSE) {
                    // conflict
                    return falsify(substitution, equalities, disjunctions, equality);
                } else {
                    if (equality.isSimplifiableByCurrentAlgorithm()) {
                        // (decompose + conflict)*
                        SymbolicUnifier unifier = new SymbolicUnifier(
                                patternFolding,
                                partialSimplification,
                                context);
                        if (!unifier.symbolicUnify(leftHandSide, rightHandSide)) {
                            return falsify(
                                    substitution,
                                    equalities,
                                    disjunctions,
                                    new Equality(
                                            unifier.unificationFailureLeftHandSide(),
                                            unifier.unificationFailureRightHandSide(),
                                            context));
                        }
                        // TODO(AndreiS): fix this in a general way
                        if (unifier.constraint().equalities.contains(equality)) {
                            pendingEqualities = pendingEqualities.plus(equality);
                            continue;
                        }
                        equalities = equalities.plusAll(i + 1, unifier.constraint().equalities);
                        equalities = equalities.plusAll(i + 1, unifier.constraint().substitution.equalities(context));
                        disjunctions = disjunctions.plusAll(unifier.constraint().disjunctions);
                    } else if (leftHandSide instanceof Variable
                            && !rightHandSide.variableSet().contains(leftHandSide)) {
                        // eliminate
                        Substitution<Variable, Term> eliminationSubstitution = getSubstitution(
                                (Variable) leftHandSide,
                                rightHandSide);
                        if (eliminationSubstitution == null) {
                            pendingEqualities = pendingEqualities.plus(equality);
                            continue;
                        }

                        substitution = Substitution.composeAndEvaluate(
                                substitution,
                                eliminationSubstitution,
                                context);
                        change = true;
                        if (substitution.isFalse(context)) {
                            return falsify(substitution, equalities, disjunctions, equality);
                        }
                    } else if (rightHandSide instanceof Variable
                            && !leftHandSide.variableSet().contains(rightHandSide)) {
                        // swap + eliminate
                        Substitution<Variable, Term> eliminationSubstitution = getSubstitution(
                                (Variable) rightHandSide,
                                leftHandSide);
                        if (eliminationSubstitution == null) {
                            pendingEqualities = pendingEqualities.plus(equality);
                            continue;
                        }

                        substitution = Substitution.composeAndEvaluate(
                                substitution,
                                eliminationSubstitution,
                                context);
                        change = true;
                        if (substitution.isFalse(context)) {
                            return falsify(substitution, equalities, disjunctions, equality);
                        }
                    } else if (leftHandSide instanceof Variable
                            && rightHandSide.isNormal()
                            && rightHandSide.variableSet().contains(leftHandSide)) {
                        // occurs
                        return falsify(substitution, equalities, disjunctions, equality);
                    } else if (rightHandSide instanceof Variable
                            && leftHandSide.isNormal()
                            && leftHandSide.variableSet().contains(rightHandSide)) {
                        // swap + occurs
                        return falsify(substitution, equalities, disjunctions, equality);
                    } else {
                        // unsimplified equation
                        pendingEqualities = pendingEqualities.plus(equality);
                    }
                }
            }
            equalities = pendingEqualities;
        } while (change);

        return ConjunctiveFormula.of(substitution, equalities, disjunctions, context);
    }

    private ConjunctiveFormula falsify(
            Substitution<Variable, Term> substitution,
            PersistentUniqueList<Equality> equalities,
            PersistentUniqueList<DisjunctiveFormula> disjunctions,
            Equality equality) {
        if (RuleAuditing.isAuditBegun()) {
            System.err.println("Unification failure: " + equality.leftHandSide()
                    + " does not unify with " + equality.rightHandSide());
        }
        return new ConjunctiveFormula(
                substitution,
                equalities,
                disjunctions,
                TruthValue.FALSE,
                equality,
                context);
    }

    public Substitution<Variable, Term> getSubstitution(Variable variable, Term term) {
        if (RewriteEngineUtils.isSubsortedEq(variable, term, context)) {
            return Substitution.singleton(variable, term);
        } else if (term instanceof Variable) {
            if (RewriteEngineUtils.isSubsortedEq(term, variable, context)) {
                return Substitution.singleton((Variable) term, variable);
            } else {
                Sort leastSort = context.definition().subsorts().getGLBSort(
                        variable.sort(),
                        term.sort());
                assert leastSort != null;

                Variable freshVariable = Variable.getAnonVariable(leastSort);
                return Substitution.<Variable, Term>singleton(variable, freshVariable)
                        .plus((Variable) term, freshVariable);
            }
        } else {
            return null;
        }
    }

    /**
     * Checks if this constraint is a substitution of the given variables.
     * <p>
     * This method is useful for checking if narrowing happens.
     */
    public boolean isMatching(Set<Variable> variables) {
        return isSubstitution() && substitution.keySet().equals(variables);
    }

    public boolean isSubstitution() {
        return equalities.isEmpty() && disjunctions.isEmpty();
    }

    public ConjunctiveFormula orientSubstitution(Set<Variable> variables) {
        if (substitution.keySet().containsAll(variables)) {
            return this;
        }

        /* compute the pre-images of each variable in the co-domain of the substitution */
        Multimap<Variable, Variable> equivalenceClasses = HashMultimap.create();
        substitution.entrySet().stream()
                .filter(e -> e.getValue() instanceof Variable)
                .forEach(e -> equivalenceClasses.put((Variable) e.getValue(), e.getKey()));

        Substitution<Variable, Variable> orientationSubstitution = Substitution.empty();
        for (Map.Entry<Variable, Collection<Variable>> entry : equivalenceClasses.asMap().entrySet()) {
            if (variables.contains(entry.getKey())) {
                Optional<Variable> replacement = entry.getValue().stream()
                        .filter(v -> v.sort().equals(entry.getKey().sort()))
                        .filter(v -> !variables.contains(v))
                        .findAny();
                if (replacement.isPresent()) {
                    orientationSubstitution = orientationSubstitution
                            .plus(entry.getKey(), replacement.get())
                            .plus(replacement.get(), entry.getKey());
                }
            }
        }

        return ((ConjunctiveFormula) substituteWithBinders(orientationSubstitution, context)).simplify();
    }

    public ConjunctiveFormula expandPatterns(boolean narrowing) {
        return new ConstrainedTerm(Bottom.BOTTOM, this).expandPatterns(narrowing).constraint();
    }

    public DisjunctiveFormula getDisjunctiveNormalForm() {
        if (disjunctions.isEmpty()) {
            return new DisjunctiveFormula(PersistentUniqueList.singleton(this), context);
        }

        ConjunctiveFormula result = ConjunctiveFormula.of(
                substitution,
                equalities,
                PersistentUniqueList.empty(),
                context);

        List<Set<ConjunctiveFormula>> collect = disjunctions.stream()
                .map(disjunction -> ImmutableSet.<ConjunctiveFormula>copyOf(disjunction.conjunctions()))
                .collect(Collectors.toList());
        return new DisjunctiveFormula(Sets.cartesianProduct(collect).stream()
                .map(result::addAll)
                .collect(Collectors.toList()), context);
    }

    public boolean checkUnsat() {
        return context.global().constraintOps.checkUnsat(this);
    }

    public boolean implies(ConjunctiveFormula constraint, Set<Variable> rightOnlyVariables) {
        // TODO(AndreiS): this can prove "stuff -> false", it needs fixing
        assert !constraint.isFalse();

        LinkedList<Pair<ConjunctiveFormula, ConjunctiveFormula>> implications = new LinkedList<>();
        implications.add(Pair.of(this, constraint));
        while (!implications.isEmpty()) {
            Pair<ConjunctiveFormula, ConjunctiveFormula> implication = implications.remove();
            ConjunctiveFormula left = implication.getLeft();
            ConjunctiveFormula right = implication.getRight();
            if (left.isFalse()) {
                continue;
            }

            if (context.global().globalOptions.debug) {
                System.err.println("Attempting to prove: \n\t" + left + "\n  implies \n\t" + right);
            }

            right = right.orientSubstitution(rightOnlyVariables);
            right = left.simplifyConstraint(right);
            right = right.orientSubstitution(rightOnlyVariables);
            if (right.isTrue() || (right.equalities().isEmpty() && rightOnlyVariables.containsAll(right.substitution().keySet()))) {
                if (context.global().globalOptions.debug) {
                    System.err.println("Implication proved by simplification");
                }
                continue;
            }

            IfThenElseFinder ifThenElseFinder = new IfThenElseFinder(context);
            right.accept(ifThenElseFinder);
            if (!ifThenElseFinder.result.isEmpty()) {
                KItem ite = ifThenElseFinder.result.get(0);
                // TODO (AndreiS): handle KList variables
                Term condition = ((KList) ite.kList()).get(0);
                if (context.global().globalOptions.debug) {
                    System.err.println("Split on " + condition);
                }
                implications.add(Pair.of(left.add(condition, BoolToken.TRUE).simplify(), right));
                implications.add(Pair.of(left.add(condition, BoolToken.FALSE).simplify(), right));
                continue;
            }

            if (!impliesSMT(left,right, rightOnlyVariables)) {
                if (context.global().globalOptions.debug) {
                    System.err.println("Failure!");
                }
                return false;
            } else {
                if (context.global().globalOptions.debug) {
                    System.err.println("Proved!");
                }
            }
        }

        return true;
    }

    /**
     * Simplifies the given constraint by eliding the equalities and substitution entries that are
     * implied by this constraint.
     */
    private ConjunctiveFormula simplifyConstraint(ConjunctiveFormula constraint) {
        Substitution<Variable, Term> simplifiedSubstitution = constraint.substitution.minusAll(
                Maps.difference(constraint.substitution, substitution).entriesInCommon().keySet());
        Predicate<Equality> inConstraint = equality -> !equalities().contains(equality)
                && !equalities().contains(new Equality(equality.rightHandSide(), equality.leftHandSide(), context));
        PersistentUniqueList<Equality> simplifiedEqualities= PersistentUniqueList.from(
                constraint.equalities().stream().filter(inConstraint).collect(Collectors.toList()));
        ConjunctiveFormula simplifiedConstraint = ConjunctiveFormula.of(
                simplifiedSubstitution,
                simplifiedEqualities,
                constraint.disjunctions,
                constraint.context);

        if (simplifiedConstraint.isTrue()) {
            return simplifiedConstraint;
        }

        // TODO(AndreiS): investigate what this code does
//        Map<Term, Term> substitution = Maps.newLinkedHashMap();
//        for (Equality e1:equalities()) {
//            if (e1.rightHandSide().isGround()) {
//                substitution.put(e1.leftHandSide(), e1.rightHandSide());
//            }
//            if (e1.leftHandSide().isGround()) {
//                substitution.put(e1.rightHandSide(), e1.leftHandSide());
//            }
//        }
//        simplifiedConstraint = (SymbolicConstraint) TermSubstitutionTransformer
//                .substitute(simplifiedConstraint, substitution, context);

        return simplifiedConstraint;
    }

    private static boolean impliesSMT(
            ConjunctiveFormula left,
            ConjunctiveFormula right,
            Set<Variable> rightOnlyVariables) {
        return left.context.global().constraintOps.impliesSMT(left, right, rightOnlyVariables);
    }

    public boolean hasMapEqualities() {
        for (Equality equality : equalities) {
            if (equality.leftHandSide() instanceof BuiltinMap
                    || equality.rightHandSide() instanceof BuiltinMap) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isExactSort() {
        return true;
    }

    @Override
    public boolean isSymbolic() {
        return true;
    }

    @Override
    public Sort sort() {
        return Sort.BOOL;
    }

    @Override
    protected boolean computeMutability() {
        return false;
    }

    @Override
    public Term toKore() {
        return toKore(context);
    }

    @Override
    public List<Term> getKComponents() {
        Stream<Term> stream = Stream.concat(
                Stream.concat(
                        substitution.equalities(context).stream().map(e -> e.toK(context)),
                        equalities.stream().map(e -> e.toK(context))),
                disjunctions.stream().map(DisjunctiveFormula::toKore));
        return stream.collect(Collectors.toList());
    }

    @Override
    public KLabel constructorLabel() {
        return KLabelConstant.of("'_andBool_", context.definition());
    }

    @Override
    public Token unit() {
        return BoolToken.TRUE;
    }

    @Override
    protected int computeHash() {
        int hashCode = 1;
        hashCode = hashCode * Utils.HASH_PRIME + substitution.hashCode();
        hashCode = hashCode * Utils.HASH_PRIME + equalities.hashCode();
        hashCode = hashCode * Utils.HASH_PRIME + disjunctions.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConjunctiveFormula)) {
            return false;
        }

        ConjunctiveFormula conjunction = (ConjunctiveFormula) object;
        return substitution.equals(conjunction.substitution)
                && equalities.equals(conjunction.equalities)
                && disjunctions.equals(conjunction.disjunctions);
    }

    @Override
    public String toString() {
        return toKore().toString();
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

}
