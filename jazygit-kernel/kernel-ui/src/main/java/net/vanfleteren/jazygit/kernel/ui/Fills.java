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
 * What the features give for the keys of the panes (ADR 172): for each {@link PaneKey}, in the order of the features,
 * what each gives in a model, or none. A pane is given the fills of the app's features, and asks them for its keys;
 * without the app, as in the tests of the views, there are {@link #NONE}.
 *
 * @param fillers what each feature gives for a key, in their order
 */
public record Fills(Map<PaneKey<?>, List<Function<Model, Option<?>>>> fillers) {

    /**
     * Nothing given for any key.
     */
    public static final Fills NONE = new Fills(LinkedHashMap.empty());

    /**
     * These fills, with {@code filler} giving for {@code key} after what is given for it already.
     */
    public <T> Fills with(PaneKey<T> key, Function<Model, Option<T>> filler) {
        Function<Model, Option<?>> any = filler::apply;
        return new Fills(fillers.put(key, List.of(any), List::appendAll));
    }

    /**
     * These fills, then those of {@code later}: for a key, what these give comes first.
     */
    public Fills andThen(Fills later) {
        return new Fills(later.fillers.foldLeft(
                fillers, (joined, entry) -> joined.put(entry._1(), entry._2(), List::appendAll)));
    }

    /**
     * What the first that gives anything for {@code key} in {@code model} gives; none when none does.
     */
    public <T> Option<T> of(PaneKey<T> key, Model model) {
        return given(key, model).headOption();
    }

    /**
     * What each that gives anything for {@code key} in {@code model} gives, in their order: for a pane that joins what
     * the features give, as the badges of a branch.
     */
    public <T> List<T> all(PaneKey<T> key, Model model) {
        return given(key, model).toList();
    }

    // Lazily, so that of asks the fillers only up to the first that gives anything.
    private <T> Iterator<T> given(PaneKey<T> key, Model model) {
        return fillers.get(key)
                .getOrElse(List.empty())
                .iterator()
                .flatMap(filler -> filler.apply(model))
                .map(key.type()::cast);
    }

    /**
     * These fills, which give nothing in a model where {@code on} does not hold (ADR 171).
     */
    public Fills gated(Predicate<Model> on) {
        return new Fills(fillers.mapValues(each -> each.map(
                filler -> (Function<Model, Option<?>>) model -> on.test(model) ? filler.apply(model) : Option.none())));
    }
}
