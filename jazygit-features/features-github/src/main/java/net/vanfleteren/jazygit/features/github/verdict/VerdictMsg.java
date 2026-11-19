package net.vanfleteren.jazygit.features.github.verdict;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.Verdict;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Approving a pull request, or asking for changes (ADR 100): asking for it, choosing which, and sending it. Writing its
 * text is a {@link net.vanfleteren.jazygit.kernel.state.compose.ComposeMsg} (ADR 115).
 */
public sealed interface VerdictMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return VerdictUpdate.update(model, this);
    }

    /**
     * The user asked to review the pull request the dialog shows.
     */
    record DialogAsked() implements VerdictMsg {}

    /**
     * The user asked to review the pull request whose files the diff shows.
     */
    record DiffAsked() implements VerdictMsg {}

    /**
     * The user chose what the review says: to approve, or to ask for changes.
     */
    record Chosen(Verdict verdict) implements VerdictMsg {}

    /**
     * The review was submitted by running {@code commands}.
     */
    record Sent(VerdictCmd.SubmitReview cmd, List<ExecutedCommand> commands) implements VerdictMsg {}

    /**
     * Submitting the review failed.
     */
    record SendFailed(VerdictCmd.SubmitReview cmd, Failure failure) implements VerdictMsg {}
}
