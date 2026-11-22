package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.State;

/**
 * What a merge key of the pull request dialog on Forgejo does, by what was read of the pull request (ADR 180): merge
 * it now as its style says, or nothing, and why. What the rules of its base branch require, approvals or checks,
 * Forgejo does not say before it is merged: it refuses the merge then, saying why.
 */
sealed interface MergeKey {

    /**
     * What the key of {@code style} does to {@code shown}, the first that fits: a pull request that is not open, a
     * draft, one that does not merge without conflicts as far as Forgejo found, or a style its repository does not
     * allow, as far as that was read, is not merged.
     */
    static MergeKey of(ShownPullRequest shown, ForgejoMergeStyle style) {
        ForgejoPullRequestDetails.Status status = shown.pullRequest().status();
        if (status.state() != State.OPEN) {
            return new Disabled(new Why.NotOpen());
        }
        if (status.draft()) {
            return new Disabled(new Why.Draft());
        }
        if (!status.mergeable()) {
            return new Disabled(new Why.Conflicts());
        }
        return shown.settings()
                .filter(settings -> !settings.allows(style))
                .<MergeKey>map(settings -> new Disabled(new Why.NotAllowed(style)))
                .getOrElse(Now::new);
    }

    /**
     * Merges it now as its style says.
     */
    record Now() implements MergeKey {}

    /**
     * Does nothing, for the reason {@code why} says.
     */
    record Disabled(Why why) implements MergeKey {}

    /**
     * Why a merge key does nothing.
     */
    sealed interface Why {

        /**
         * It is merged or closed.
         */
        record NotOpen() implements Why {}

        /**
         * It is a draft, which Forgejo does not merge.
         */
        record Draft() implements Why {}

        /**
         * Forgejo found it does not merge without conflicts, or is still finding out.
         */
        record Conflicts() implements Why {}

        /**
         * Its repository does not allow merging as {@code style}.
         */
        record NotAllowed(ForgejoMergeStyle style) implements Why {}
    }
}
