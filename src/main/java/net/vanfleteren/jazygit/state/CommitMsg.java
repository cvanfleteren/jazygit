package net.vanfleteren.jazygit.state;

public sealed interface CommitMsg extends Msg {

    /**
     * The user asked to commit: the message is asked for, after staging everything if nothing is
     * staged yet.
     */
    record Requested() implements CommitMsg {
    }

    record StageAllConfirmed() implements CommitMsg {
    }

    record StageAllCancelled() implements CommitMsg {
    }

    /**
     * Everything was staged for a commit.
     */
    record StagedForCommit() implements CommitMsg {
    }

    record StageForCommitFailed(String message) implements CommitMsg {
    }

    record Cancelled() implements CommitMsg {
    }

    record Confirmed(String summary, String description) implements CommitMsg {
    }

    record Done() implements CommitMsg {
    }

    record Failed(String message) implements CommitMsg {
    }
}
