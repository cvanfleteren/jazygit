package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.Iterator;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What the features give for the slots (ADR 172, 202): for each {@link Slot}, in the order of the features, what each
 * gives in a model, which may be nothing or several. A part of the app is given the fills of the app's features, and
 * asks them for its slots; without the app, as in the tests of the views, there are {@link #NONE}.
 *
 * <p>What a feature gives may depend on what the features give for other slots: a filler is handed the fills it is
 * asked through (ADR 211), as the branches pane's times are those of the badges the forges give.
 *
 * @param fillers what each feature gives for a slot, in their order, of a model and the fills they are asked through
 */
public record Fills(Map<Slot<?>, List<BiFunction<Model, Fills, Iterable<?>>>> fillers) {

    /**
     * Nothing given for any slot.
     */
    public static final Fills NONE = new Fills(LinkedHashMap.empty());

    /**
     * These fills, with {@code filler} giving for {@code slot} after what is given for it already. An {@code Option}
     * gives one or nothing, a {@code List} as many as it holds.
     */
    public <T> Fills with(Slot<T> slot, Function<Model, ? extends Iterable<? extends T>> filler) {
        return reading(slot, (model, fills) -> filler.apply(model));
    }

    /**
     * These fills, with {@code filler} giving for {@code slot} after what is given for it already, from a model and
     * the fills it is asked through, whose other slots it may ask (ADR 211).
     */
    public <T> Fills reading(Slot<T> slot, BiFunction<Model, Fills, ? extends Iterable<? extends T>> filler) {
        BiFunction<Model, Fills, Iterable<?>> any = filler::apply;
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
    // Unchecked, but safe while what is given comes through with, which takes for a slot only what gives its type: the
    // record's own constructor checks nothing, and is for these methods. Two slots of one name would break it too,
    // which SlotNamesTest keeps out (ADR 203).
    @SuppressWarnings("unchecked")
    private <T> Iterator<T> given(Slot<T> slot, Model model) {
        return fillers.get(slot)
                .getOrElse(List.empty())
                .iterator()
                .flatMap(filler -> filler.apply(model, this))
                .map(given -> (T) given);
    }

    /**
     * These fills, which give nothing in a model where {@code on} does not hold (ADR 171).
     */
    public Fills gated(Predicate<Model> on) {
        return new Fills(fillers.mapValues(each -> each.map(filler -> (BiFunction<Model, Fills, Iterable<?>>)
                (model, fills) -> on.test(model) ? filler.apply(model, fills) : List.empty())));
    }
}
