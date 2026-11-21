package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
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
     * This part, stating what the branches are built on as {@code stating} finds it in its value (ADR 169).
     */
    public Part<S> stating(States<S> stating) {
        return new Part<>(name, initial, hooks.withStates(stating));
    }

    /**
     * This part, naming the repository on the host of its remotes as {@code naming} finds it in its value (ADR 169).
     */
    public Part<S> naming(Names<S> naming) {
        return new Part<>(name, initial, hooks.withNames(naming));
    }

    /**
     * This part, stating the remarks of a change whose files are listed as {@code remarking} finds them in its value
     * (ADR 171).
     */
    public Part<S> remarking(Remarking<S> remarking) {
        return new Part<>(name, initial, hooks.withRemarking(remarking));
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
     * What {@code value}, a part, states the branches are built on (ADR 169).
     */
    @FunctionalInterface
    public interface States<S> {
        StatedBases bases(S value);
    }

    /**
     * The repository on the host of the remotes, as {@code owner/name}, if {@code value}, a part, knows it
     * (ADR 169).
     */
    @FunctionalInterface
    public interface Names<S> {
        Option<String> repository(S value);
    }

    /**
     * The remarks {@code value}, a part, states for {@code changes}, if it states any (ADR 171).
     */
    @FunctionalInterface
    public interface Remarking<S> {
        Option<StatedRemarks> remarks(S value, Changes changes);
    }

    /**
     * What the kernel asks of a part (ADR 158, 169, 171).
     */
    public record Hooks<S>(
            Rereads<S> rereads,
            KnowsCommit<S> knowsCommit,
            Reacting<S> reacting,
            States<S> states,
            Names<S> names,
            Remarking<S> remarking) {

        private static final Hooks<Object> NONE = new Hooks<>(
                (model, value) -> List.empty(),
                (value, sha) -> Option.none(),
                (model, value, event) -> Update.Next.of(model),
                value -> StatedBases.NONE,
                value -> Option.none(),
                (value, changes) -> Option.none());

        /**
         * No hooks: the part reads nothing again, knows no commit, answers nothing, states no bases, names no
         * repository and states no remarks.
         */
        @SuppressWarnings("unchecked") // None of them looks at the value.
        public static <S> Hooks<S> none() {
            return (Hooks<S>) NONE;
        }

        Hooks<S> withRereads(Rereads<S> rereads) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }

        Hooks<S> withKnowsCommit(KnowsCommit<S> knowsCommit) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }

        Hooks<S> withReacting(Reacting<S> reacting) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }

        Hooks<S> withStates(States<S> states) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }

        Hooks<S> withNames(Names<S> names) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }

        Hooks<S> withRemarking(Remarking<S> remarking) {
            return new Hooks<>(rereads, knowsCommit, reacting, states, names, remarking);
        }
    }
}
