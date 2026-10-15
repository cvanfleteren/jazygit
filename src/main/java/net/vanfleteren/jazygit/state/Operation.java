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
    REWORD("log.title.reword", "error.reword.failed"),
    SQUASH("log.title.squash", "error.squash.failed"),
    FIXUP("log.title.fixup", "error.fixup.failed"),
    RESET("log.title.reset", "error.reset.failed"),
    DISCARD("log.title.discard", "error.discard.failed"),
    CHECKOUT("log.title.checkout", "error.checkout.failed"),
    NEW_BRANCH("log.title.newBranch", "error.newBranch.failed"),
    DELETE_BRANCH("log.title.deleteBranch", "error.deleteBranch.failed"),
    PUSH("log.title.push", "error.push.failed"),
    PULL("log.title.pull", "error.pull.failed"),
    FAST_FORWARD("log.title.fastForward", "error.fastForward.failed"),
    REBASE("log.title.rebase", "error.rebase.failed"),
    MERGE("log.title.merge", "error.merge.failed"),
    CREATE_PR("log.title.createPr", "error.createPr.failed"),
    OPEN_PR("log.title.openPr", "error.openPr.failed"),
    MERGE_PR("log.title.mergePr", "error.mergePr.failed"),
    CLOSE_PR("log.title.closePr", "error.closePr.failed"),
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
}
