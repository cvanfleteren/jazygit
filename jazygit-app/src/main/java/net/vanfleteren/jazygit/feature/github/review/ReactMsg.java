package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

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
