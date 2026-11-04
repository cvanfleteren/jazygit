package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * A change made to the stacks on GitHub that the pull requests read from it may not show yet: GitHub can list them as
 * they were for a while after it made the change. Until they show it, or until {@link #until()}, they are read again
 * every tick (ADR 73).
 */
public sealed interface StackChange {

    /**
     * When the pull requests are no longer read again for it, whether they show it or not.
     */
    Instant until();

    /**
     * Whether {@code open}, the open pull requests read from GitHub, show the change.
     */
    boolean shownIn(Map<BranchName, PullRequest> open);

    /**
     * Pull requests {@code pullRequests} were stacked: on stack {@code stack}, when they were put on one.
     */
    record Stacked(List<Integer> pullRequests, Option<Integer> stack, Instant until) implements StackChange {

        @Override
        public boolean shownIn(Map<BranchName, PullRequest> open) {
            List<Option<Integer>> stacks = pullRequests.map(number -> open.values()
                    .find(pr -> pr.number() == number)
                    .flatMap(PullRequest::stack)
                    .map(PullRequestStack::number));
            return stacks.forAll(Option::isDefined)
                    && stacks.distinct().size() == 1
                    && stack.forAll(number -> stacks.head().contains(number));
        }
    }

    /**
     * Stack {@code stack} was taken apart.
     */
    record Unstacked(int stack, Instant until) implements StackChange {

        @Override
        public boolean shownIn(Map<BranchName, PullRequest> open) {
            return open.values().forAll(pr -> pr.stack().forAll(on -> on.number() != stack));
        }
    }
}
