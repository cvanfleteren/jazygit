package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Approving a pull request, or asking for changes (ADR 99): asking for it, writing its text, in the popup or in the
 * editor, and sending it.
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
     * The user changed the text of the review to {@code text}.
     */
    record Edited(String text) implements VerdictMsg {}

    /**
     * The user asked to send the review.
     */
    record Confirmed() implements VerdictMsg {}

    record Cancelled() implements VerdictMsg {}

    /**
     * The user asked to write the text of the review in the editor.
     */
    record EditorRequested() implements VerdictMsg {}

    /**
     * The editor ran {@code commands}, and saved the text as {@code text}.
     */
    record EditorClosed(String text, List<ExecutedCommand> commands) implements VerdictMsg {}

    record EditorFailed(Failure failure) implements VerdictMsg {}

    /**
     * The review was submitted by running {@code commands}.
     */
    record Sent(VerdictCmd.SubmitReview cmd, List<ExecutedCommand> commands) implements VerdictMsg {}

    /**
     * Submitting the review failed.
     */
    record SendFailed(VerdictCmd.SubmitReview cmd, Failure failure) implements VerdictMsg {}
}
