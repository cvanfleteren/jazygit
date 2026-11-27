package net.vanfleteren.jazygit.features.github.show;

import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.AutoMerge;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Merging;

/**
 * What a merge key of the pull request dialog does, by what GitHub says of the pull request (ADR 112): merge it now,
 * turn auto-merge on so GitHub merges it once it is ready, turn auto-merge off, or nothing, and why.
 */
public sealed interface MergeKey {

    /**
     * What the key of {@code method} does to {@code pr}, the first row of the table of ADR 112 that fits. A stacked
     * one is merged now with the ones below it, as GitHub has no auto-merge for stacked pull requests. While its
     * auto-merge is on, the keys only turn it off.
     */
    static MergeKey of(PullRequestDetails pr, MergeMethod method) {
        Merging merging = pr.merging();
        return pr.stack().isDefined()
                ? new Now()
                : merging.autoMerge()
                        .<MergeKey>map(on -> on.method() == method && merging.canDisable()
                                ? new Cancel()
                                : new Disabled(new Why.AutoMergeOn(on)))
                        .getOrElse(() -> switch (merging.state()) {
                            case BLOCKED ->
                                merging.canEnable()
                                        ? new WhenReady()
                                        : new Disabled(merging.allowed() ? new Why.Blocked() : new Why.NotAllowed());
                            case BEHIND -> new Disabled(new Why.Behind());
                            default -> new Now();
                        });
    }

    /**
     * Merges it now, as before auto-merge (ADR 24).
     */
    record Now() implements MergeKey {}

    /**
     * Turns auto-merge on: GitHub merges it once nothing blocks it any more.
     */
    record WhenReady() implements MergeKey {}

    /**
     * Turns its auto-merge off, which is on with the method of the key.
     */
    record Cancel() implements MergeKey {}

    /**
     * Does nothing, for the reason {@code why} says.
     */
    record Disabled(Why why) implements MergeKey {}

    /**
     * Why a merge key does nothing.
     */
    sealed interface Why {

        /**
         * Its auto-merge is on as {@code autoMerge} says, with another method or by someone the viewer cannot turn it
         * off for.
         */
        record AutoMergeOn(AutoMerge autoMerge) implements Why {}

        /**
         * It is blocked, and its repository does not allow auto-merge.
         */
        record NotAllowed() implements Why {}

        /**
         * It is blocked, and GitHub does not let the viewer turn auto-merge on.
         */
        record Blocked() implements Why {}

        /**
         * Its base branch has to be caught up with before GitHub merges it.
         */
        record Behind() implements Why {}
    }
}
