package net.vanfleteren.jazygit.features.github.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;
import org.junit.jupiter.api.Test;

/**
 * The repositories of the remotes, read with the pull requests, so that those of one's own fork are of its branches
 * (ADR 177).
 */
class LoadRepositoryPullRequestsTest {

    private static Env env(Either<GitError, Map<RemoteName, String>> urls) {
        return new Env(StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitError, Map<RemoteName, String>> urls() {
                return urls;
            }
        }));
    }

    @Test
    void eachRemoteIsKnownByItsRepository() {
        assertEquals(
                HashMap.of(new RemoteName("origin"), "me/repo", new RemoteName("upstream"), "project/repo"),
                LoadRepositoryPullRequests.remotes(env(Either.right(HashMap.of(
                        new RemoteName("origin"), "git@github.com:me/repo.git",
                        new RemoteName("upstream"), "https://github.com/project/repo",
                        new RemoteName("local"), "/srv/git/repo.git")))));
    }

    @Test
    void remotesThatCannotBeListedAreNone() {
        assertEquals(
                HashMap.empty(),
                LoadRepositoryPullRequests.remotes(env(Either.left(new GitError.Interrupted("remote")))));
    }
}
