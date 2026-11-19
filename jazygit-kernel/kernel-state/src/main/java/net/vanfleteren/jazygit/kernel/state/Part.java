package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.load.Targeted;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * A part of the {@link Model} that a feature keeps for itself, declared as a constant in the feature's own package
 * (ADR 157), with what the kernel asks of it (ADR 158): the model holds it without naming it, as it holds a
 * {@link net.vanfleteren.jazygit.kernel.state.popup.Popup}. A part that was never set, or was set back to its initial value,
 * is not kept, so two models with the same parts are equal; only a part that is kept is asked anything.
 *
 * <p>A part is kept under its name: no two parts may share one, which {@code PartNamesTest} in the app checks for
 * every part constant.
 *
 * @param name    the name the part is kept under; it starts with the root package of the feature's module
 *                ({@code github.builds})
 * @param initial its value before the feature set it
 * @param hooks   what the kernel asks of it; nothing by default
 * @param <S>     the type of its value, a value as the rest of the model is
 */
public record Part<S>(String name, S initial, Hooks<S> hooks) {

    /**
     * The part {@code name}, whose value is {@code initial} until the feature sets it, of which the kernel asks
     * nothing.
     */
    public static <S> Part<S> of(String name, S initial) {
        return new Part<>(name, initial, Hooks.none());
    }

    /**
     * This part, reading again what {@code rereads} gives for it: the loads of what it shows, which the kernel reads
     * again once they failed, or with the diff options changed.
     */
    public Part<S> rereading(Rereads<S> rereads) {
        return new Part<>(name, initial, hooks.withRereads(rereads));
    }

    /**
     * This part, knowing the commits {@code knowsCommit} finds in it, which {@link Model#commit(Sha)} gives too.
     */
    public Part<S> knowingCommits(KnowsCommit<S> knowsCommit) {
        return new Part<>(name, initial, hooks.withKnowsCommit(knowsCommit));
    }

    /**
     * This part, answering what happened in the kernel with what {@code reacting} makes of the model.
     */
    public Part<S> reacting(Reacting<S> reacting) {
        return new Part<>(name, initial, hooks.withReacting(reacting));
    }

    /**
     * Where this part sits in a model.
     */
    public Lens<Model, S> lens() {
        return Lens.of(model -> model.part(this), (model, value) -> model.withPart(this, value));
    }

    /**
     * The loads of what {@code value}, a part in {@code model}, shows.
     */
    @FunctionalInterface
    public interface Rereads<S> {
        List<Targeted<?>> of(Model model, S value);
    }

    /**
     * The commit {@code sha}, if {@code value}, a part, lists it.
     */
    @FunctionalInterface
    public interface KnowsCommit<S> {
        Option<Commit> commit(S value, Sha sha);
    }

    /**
     * What {@code model}, which keeps {@code value} in a part, becomes now that {@code event} happened.
     */
    @FunctionalInterface
    public interface Reacting<S> {
        Update.Next to(Model model, S value, KernelEvent event);
    }

    /**
     * What the kernel asks of a part (ADR 158).
     */
    public record Hooks<S>(Rereads<S> rereads, KnowsCommit<S> knowsCommit, Reacting<S> reacting) {

        private static final Hooks<Object> NONE = new Hooks<>(
                (model, value) -> List.empty(),
                (value, sha) -> Option.none(),
                (model, value, event) -> Update.Next.of(model));

        /**
         * No hooks: the part reads nothing again, knows no commit and answers nothing.
         */
        @SuppressWarnings("unchecked") // None of them looks at the value.
        public static <S> Hooks<S> none() {
            return (Hooks<S>) NONE;
        }

        Hooks<S> withRereads(Rereads<S> rereads) {
            return new Hooks<>(rereads, knowsCommit, reacting);
        }

        Hooks<S> withKnowsCommit(KnowsCommit<S> knowsCommit) {
            return new Hooks<>(rereads, knowsCommit, reacting);
        }

        Hooks<S> withReacting(Reacting<S> reacting) {
            return new Hooks<>(rereads, knowsCommit, reacting);
        }
    }
}
