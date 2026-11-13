package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Commenting on the pull request that is shown: asking for it, and sending it. Writing the comment is a
 * {@link net.vanfleteren.jazygit.state.compose.ComposeMsg} (ADR 115).
 */
public sealed interface CommentMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommentUpdate.update(model, this);
    }

    /**
     * The user asked to comment on the pull request that is shown.
     */
    record Requested() implements CommentMsg {}

    /**
     * The comment was added by running {@code commands}.
     */
    record Sent(CommentCmd.AddComment cmd, List<ExecutedCommand> commands) implements CommentMsg {}

    /**
     * Adding the comment failed.
     */
    record SendFailed(CommentCmd.AddComment cmd, Failure failure) implements CommentMsg {}
}
