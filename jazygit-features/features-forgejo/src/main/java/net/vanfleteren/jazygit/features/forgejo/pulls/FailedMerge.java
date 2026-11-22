package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * A merge of a pull request on Forgejo that failed (ADR 180), kept in Forgejo's part, for the pull request's marker
 * to show once Forgejo has markers; nothing reads it yet. It is replaced by the next merge that fails, and dropped once
 * one succeeds or the pull request is no longer open.
 *
 * @param number   the number of the pull request
 * @param style    how it was to be merged
 * @param failure  why it failed
 * @param standing how the pull request stood when it failed, so that a failure gives way once it changed, as GitHub's
 *                 does (ADR 119)
 */
public record FailedMerge(long number, ForgejoMergeStyle style, Failure failure, Standing standing) {

    /**
     * How a pull request stood, as it was last read before it was merged: its head alone, as only one that is no
     * draft and merges without conflicts is merged, so that those never differ.
     *
     * @param head the commit its head was at
     */
    public record Standing(Sha head) {}
}
