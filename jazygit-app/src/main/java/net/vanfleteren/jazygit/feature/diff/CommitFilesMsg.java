package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface CommitFilesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitFilesUpdate.update(model, this);
    }

    /**
     * The user asked for the files {@code of} changed, from the pane {@code pane}.
     */
    record Opened(CommitFilesView.Changes of, String pane) implements CommitFilesMsg {}

    /**
     * The user closed the files of the commit or the stash, to see the files of the working tree again.
     */
    record Closed() implements CommitFilesMsg {}

    /**
     * The review threads of pull request {@code number} were read (ADR 55).
     */
    record RemarksLoaded(int number, ReviewRemarks remarks) implements CommitFilesMsg {}

    /**
     * Reading the review threads of pull request {@code number} failed.
     */
    record RemarksFailed(int number, Failure failure) implements CommitFilesMsg {}

    /**
     * The user asked to open every review thread, the resolved ones and the hidden comments too, or to close them
     * again.
     */
    record RemarksToggled() implements CommitFilesMsg {}

    /**
     * The user asked to list the next of the files of a pull request: all of them, those with an unresolved review
     * thread, or those with any (ADR 57).
     */
    record ListedCycled() implements CommitFilesMsg {}

    /**
     * The user asked to read the review threads of the pull request again.
     */
    record RemarksRefreshed() implements CommitFilesMsg {}
}
