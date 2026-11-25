package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.Iterator;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What the features give for the slots (ADR 172, 202): for each {@link Slot}, in the order of the features, what each
 * gives in a model, which may be nothing or several. A part of the app is given the fills of the app's features, and
 * asks them for its slots; without the app, as in the tests of the views, there are {@link #NONE}.
 *
 * @param fillers what each feature gives for a slot, in their order
 */
public record Fills(Map<Slot<?>, List<Function<Model, Iterable<?>>>> fillers) {

    /**
     * Nothing given for any slot.
     */
    public static final Fills NONE = new Fills(LinkedHashMap.empty());

    /**
     * These fills, with {@code filler} giving for {@code slot} after what is given for it already. An {@code Option}
     * gives one or nothing, a {@code List} as many as it holds.
     */
    public <T> Fills with(Slot<T> slot, Function<Model, ? extends Iterable<? extends T>> filler) {
        Function<Model, Iterable<?>> any = filler::apply;
        return new Fills(fillers.put(slot, List.of(any), List::appendAll));
    }

    /**
     * These fills, then those of {@code later}: for a slot, what these give comes first.
     */
    public Fills andThen(Fills later) {
        return new Fills(later.fillers.foldLeft(
                fillers, (joined, entry) -> joined.put(entry._1(), entry._2(), List::appendAll)));
    }

    /**
     * The first that is given for {@code slot} in {@code model}; none when nothing is.
     */
    public <T> Option<T> of(Slot<T> slot, Model model) {
        return given(slot, model).headOption();
    }

    /**
     * All that is given for {@code slot} in {@code model}, in the order of the features: for a part that joins what
     * the features give, as the badges of a branch.
     */
    public <T> List<T> all(Slot<T> slot, Model model) {
        return given(slot, model).toList();
    }

    // Lazily, so that of asks the fillers only up to the first that gives anything.
    private <T> Iterator<T> given(Slot<T> slot, Model model) {
        return fillers.get(slot)
                .getOrElse(List.empty())
                .iterator()
                .flatMap(filler -> filler.apply(model))
                .map(slot.type()::cast);
    }

    /**
     * These fills, which give nothing in a model where {@code on} does not hold (ADR 171).
     */
    public Fills gated(Predicate<Model> on) {
        return new Fills(fillers.mapValues(each -> each.map(filler ->
                (Function<Model, Iterable<?>>) model -> on.test(model) ? filler.apply(model) : List.empty())));
    }
}
