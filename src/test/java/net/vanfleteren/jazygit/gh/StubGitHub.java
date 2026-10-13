package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A {@link GitHub} for tests: a repository without pull requests, whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubGitHub extends GitHub {

    /**
     * A GitHub repository without pull requests.
     */
    static StubGitHub empty() {
        return new StubGitHub() {
        };
    }

    @Override
    default Either<GhError, List<PullRequest>> pullRequests() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GhFailure, Created> createPullRequest(String head, String base) {
        return Either.right(new Created(Option.none(),
                List.of(ExecutedCommand.of("gh", "pr", "create", "--head", head, "--base", base, "--fill"))));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", String.valueOf(number), "--web")));
    }
}
