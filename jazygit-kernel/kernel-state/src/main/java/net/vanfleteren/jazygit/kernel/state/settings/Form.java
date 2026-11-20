package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;
import java.util.function.BiFunction;

/**
 * How an entry of a list in the settings panel is edited (ADR 164): its fields, in order, what a new one starts as,
 * and what is wrong with one, given the other entries of the list before and after it, as a name that is taken.
 *
 * @param blank    what a new entry starts as
 * @param fields   the fields, in the order the form shows them
 * @param validate what is wrong with an entry, given the other entries of the list
 * @param <E>      what an entry is
 */
public record Form<E>(E blank, List<Field<E>> fields, BiFunction<E, Others<E>, List<FieldProblem>> validate) {

    /**
     * What is wrong with {@code entry}, given {@code others}, the other entries of the list.
     */
    public List<FieldProblem> problems(E entry, Others<E> others) {
        return validate.apply(entry, others);
    }

    /**
     * Whether {@code entry} can be saved: nothing wrong with it blocks that.
     */
    public boolean savable(E entry, Others<E> others) {
        return problems(entry, others).forAll(problem -> !problem.blocks());
    }
}
