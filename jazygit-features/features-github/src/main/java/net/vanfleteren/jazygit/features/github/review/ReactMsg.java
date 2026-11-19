package net.vanfleteren.jazygit.features.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.Reaction;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;

/**
 * Reacting to a comment of a review thread of the diff of a pull request's files, from its cursor (ADR 86).
 */
public sealed interface ReactMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ReactUpdate.update(model, this);
    }

    /**
     * The user asked to react to the comment of the cursor {@code at} (ADR 86).
     */
    record ReactAsked(DiffCursorAt at) implements ReactMsg {}

    /**
     * The user chose {@code reaction}: to give it, or to take it back when they gave it.
     */
    record ReactionChosen(Reaction reaction) implements ReactMsg {}

    /**
     * The reaction was given or taken back by running {@code commands}.
     */
    record Reacted(ReactCmd.React cmd, List<ExecutedCommand> commands) implements ReactMsg {}

    /**
     * Giving or taking back the reaction failed.
     */
    record ReactFailed(ReactCmd.React cmd, Failure failure) implements ReactMsg {}
}
