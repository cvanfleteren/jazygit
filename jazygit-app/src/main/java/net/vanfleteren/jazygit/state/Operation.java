package net.vanfleteren.jazygit.state;

/**
 * An operation that changes the repository. Its git commands are logged under its title, and its failure
 * is reported with its error message.
 *
 * @param logTitleKey the message key of the command log title
 * @param errorKey    the message key of its failure, which is given the subject of the operation (if it
 *                    has one, see {@link OperationCmd#subject()}) and why it failed
 */
public enum Operation {
    STAGE("log.title.stage", "error.staging.failed"),
    UNSTAGE("log.title.unstage", "error.staging.failed"),
    COMMIT("log.title.commit", "error.commit.failed"),
    AMEND("log.title.amend", "error.amend.failed"),
    AMEND_COMMIT("log.title.amendCommit", "error.amendCommit.failed"),
    REWORD("log.title.reword", "error.reword.failed"),
    SQUASH("log.title.squash", "error.squash.failed"),
    FIXUP("log.title.fixup", "error.fixup.failed"),
    DROP("log.title.drop", "error.drop.failed"),
    RESET("log.title.reset", "error.reset.failed"),
    CHERRY_PICK("log.title.cherryPick", "error.cherryPick.failed"),
    DISCARD("log.title.discard", "error.discard.failed"),
    EDIT("log.title.edit", "error.edit.failed"),
    EDIT_MESSAGE("log.title.editMessage", "error.editMessage.failed"),
    CHECKOUT("log.title.checkout", "error.checkout.failed"),
    NEW_BRANCH("log.title.newBranch", "error.newBranch.failed"),
    RENAME_BRANCH("log.title.renameBranch", "error.renameBranch.failed"),
    DELETE_BRANCH("log.title.deleteBranch", "error.deleteBranch.failed"),
    ADD_WORKTREE("log.title.addWorktree", "error.addWorktree.failed"),
    REMOVE_WORKTREE("log.title.removeWorktree", "error.removeWorktree.failed"),
    PUSH("log.title.push", "error.push.failed"),
    PULL("log.title.pull", "error.pull.failed"),
    FAST_FORWARD("log.title.fastForward", "error.fastForward.failed"),
    REBASE("log.title.rebase", "error.rebase.failed"),
    MERGE("log.title.merge", "error.merge.failed"),
    CONTINUE("log.title.continue", "error.continue.failed"),
    ABORT("log.title.abort", "error.abort.failed"),
    SKIP("log.title.skip", "error.skip.failed"),
    CREATE_PR("log.title.createPr", "error.createPr.failed"),
    OPEN_PR("log.title.openPr", "error.openPr.failed"),
    MERGE_PR("log.title.mergePr", "error.mergePr.failed"),
    CLOSE_PR("log.title.closePr", "error.closePr.failed"),
    READY_PR("log.title.readyPr", "error.readyPr.failed"),
    DRAFT_PR("log.title.draftPr", "error.draftPr.failed"),
    BROWSE("log.title.browse", "error.browse.failed"),
    STASH("log.title.stash", "error.stash.failed"),
    STASH_POP("log.title.stashPop", "error.stashPop.failed"),
    STASH_DROP("log.title.stashDrop", "error.stashDrop.failed");

    private final String logTitleKey;
    private final String errorKey;

    Operation(String logTitleKey, String errorKey) {
        this.logTitleKey = logTitleKey;
        this.errorKey = errorKey;
    }

    public String logTitleKey() {
        return logTitleKey;
    }

    public String errorKey() {
        return errorKey;
    }

    /**
     * Whether it waits until the repository is in the middle of nothing, and has no conflicts: it moves HEAD or
     * rewrites history, which git refuses then, or which would get mixed up with what is in progress.
     */
    public boolean waitsForUnfinished() {
        return switch (this) {
            case CHECKOUT, MERGE, REBASE, PULL, CHERRY_PICK, SQUASH, FIXUP, DROP, REWORD, AMEND_COMMIT -> true;
            default -> false;
        };
    }
}
