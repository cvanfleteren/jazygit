package net.vanfleteren.jazygit.features.github;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.account.StubAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import org.junit.jupiter.api.Test;

/**
 * GitHub applies to a repository with a remote on GitHub and gh found (ADR 171).
 */
class DetectGitHubTest {

    @Test
    void aRemoteOnGitHubWithGhFoundIsGitHubs() {
        assertEquals(applies(true), detect("https://github.com/owner/repo.git", StubGitHub.of()));
        assertEquals(applies(true), detect("git@acme.ghe.com:owner/repo.git", StubGitHub.of()));
    }

    @Test
    void withoutARemoteOnGitHubItIsNot() {
        assertEquals(applies(false), detect("https://gitlab.com/owner/repo.git", StubGitHub.of()));
        assertEquals(applies(false), new DetectGitHub("gh").run(env(HashMap.empty(), StubGitHub.of())));
    }

    @Test
    void aHostGhIsLoggedInToIsGitHubs() {
        GitHub loggedIn = StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Either<GhError, List<GhLogin>> logins() {
                return Either.right(List.of(new GhLogin("git.acme.com", "ann", true, true)));
            }
        });
        assertEquals(applies(true), detect("https://git.acme.com/owner/repo.git", loggedIn));
    }

    @Test
    void withoutGhItIsNot() {
        GitHub missing = StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Either<GhError, String> version(String executable) {
                return Either.left(new GhError.GhUnavailable(new java.io.IOException("no gh")));
            }
        });
        assertEquals(applies(false), detect("https://github.com/owner/repo.git", missing));
    }

    private static FeatureMsg.Applies applies(boolean on) {
        return new FeatureMsg.Applies(GitHubFeature.ID, on);
    }

    private static Object detect(String url, GitHub gh) {
        return new DetectGitHub("gh").run(env(HashMap.of(new RemoteName("origin"), url), gh));
    }

    private static Env env(Map<RemoteName, String> urls, GitHub gh) {
        return new Env(StubGit.of().withRemotes(new StubRemotes() {
                    @Override
                    public Either<GitError, Map<RemoteName, String>> urls() {
                        return Either.right(urls);
                    }
                }))
                .with(GitHubServices.GH, gh);
    }
}
