package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;

/**
 * What the features answer of a {@link KernelEvent}, whichever part they keep (ADR 158): the app gathers them from the
 * features and gives them to the model, which holds them without naming a feature, as it holds its parts. A part
 * answers an event only while it is kept; a feature that has to answer always, as one that reads from the forge does,
 * declares a reaction.
 *
 * @param all the reactions, in the order the features are listed
 */
public record Reactions(List<Reaction> all) {

    /**
     * No feature answers.
     */
    public static final Reactions NONE = new Reactions(List.empty());

    /**
     * What {@code next} becomes once each reaction answered {@code event}, in order: each with the model the earlier
     * ones left, the commands each asks for coming after those that came before.
     */
    Update.Next to(Update.Next next, KernelEvent event) {
        return all.foldLeft(next, (answered, reaction) -> answered.then(model -> reaction.to(model, event)));
    }

    /**
     * What {@code model} becomes once {@code cmds}, which an update asked for, started, as each reaction makes of it,
     * in order (ADR 169).
     */
    Model started(Model model, List<Cmd> cmds) {
        return all.foldLeft(model, (told, reaction) -> reaction.started(told, cmds));
    }

    /**
     * What a feature does when {@code event} happened to {@code model}: nothing, or what changes the model and
     * the commands to run, which it answers with {@code Update.Next.of(model)} when the event is none of its concern.
     */
    @FunctionalInterface
    public interface Reaction {
        Update.Next to(Model model, KernelEvent event);

        /**
         * What {@code model} becomes once {@code cmds} started, as the ids they are given are known then: as a read
         * of a forge, which is told apart from those before it by the id it gets as it starts (ADR 119, 169). It
         * asks for nothing; by default, it changes nothing.
         */
        default Model started(Model model, List<Cmd> cmds) {
            return model;
        }
    }
}
