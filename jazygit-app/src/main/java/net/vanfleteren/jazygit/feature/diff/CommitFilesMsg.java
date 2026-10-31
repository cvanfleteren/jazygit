package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
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
     * The review threads of pull request {@code number} were read (ADR 55), after what was sent for the threads
     * {@code settled} (ADR 60).
     */
    record RemarksLoaded(int number, ReviewRemarks remarks, Set<String> settled) implements CommitFilesMsg {

        public RemarksLoaded(int number, ReviewRemarks remarks) {
            this(number, remarks, HashSet.empty());
        }
    }

    /**
     * Reading the review threads of pull request {@code number} failed, after what was sent for the threads
     * {@code settled}.
     */
    record RemarksFailed(int number, Failure failure, Set<String> settled) implements CommitFilesMsg {

        public RemarksFailed(int number, Failure failure) {
            this(number, failure, HashSet.empty());
        }
    }

    /**
     * The user asked to list the next of the files of a pull request: all of them, those with an unresolved review
     * thread, or those with any (ADR 57).
     */
    record ListedCycled() implements CommitFilesMsg {}

    /**
     * The user asked to read the review threads of the pull request again.
     */
    record RemarksRefreshed() implements CommitFilesMsg {}

    /**
     * The review threads and comments of pull request {@code number} collapsed or opened against their default in an
     * earlier session were read, each with whether it was collapsed by default then (ADR 63).
     */
    record CollapsedLoaded(int number, Map<String, Boolean> kept) implements CommitFilesMsg {}

    /**
     * The review threads and comments collapsed or opened were kept for the next session, or that failed: nothing
     * changes.
     */
    record CollapsedKept() implements CommitFilesMsg {}
}
