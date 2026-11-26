package net.vanfleteren.jazygit.features.github;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.account.StubAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteUrl;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.junit.jupiter.api.Test;

/**
 * GitHub claims the remotes of a repository on GitHub, with gh found (ADR 171, 177), from which the kernel decides
 * whether it is the repository's forge.
 */
class DetectGitHubTest {

    @Test
    void aRemoteOnGitHubWithGhFoundIsGitHubs() {
        assertEquals(List.of("origin"), claimed(detect("https://github.com/owner/repo.git", StubGitHub.of())));
        assertEquals(List.of("origin"), claimed(detect("git@acme.ghe.com:owner/repo.git", StubGitHub.of())));
    }

    @Test
    void withoutARemoteOnGitHubItClaimsNone() {
        assertEquals(List.empty(), claimed(detect("https://gitlab.com/owner/repo.git", StubGitHub.of())));
        assertEquals(List.empty(), claimed(new DetectGitHub("gh").run(env(HashMap.empty(), StubGitHub.of()))));
    }

    @Test
    void itClaimsEachRemoteOnGitHubAndNoneOnAnotherForge() {
        Msg detected = new DetectGitHub("gh")
                .run(env(
                        HashMap.of(
                                new RemoteName("origin"), new RemoteUrl("https://github.com/me/repo.git"),
                                new RemoteName("upstream"), new RemoteUrl("https://github.com/owner/repo.git"),
                                new RemoteName("codeberg"), new RemoteUrl("https://codeberg.org/owner/repo.git")),
                        StubGitHub.of()));

        assertEquals(List.of("origin", "upstream"), claimed(detected));
        assertEquals(
                "https://github.com/owner/repo.git",
                ((FeatureMsg.Claimed) detected)
                        .claim()
                        .claimed()
                        .get(new RemoteName("upstream"))
                        .get());
    }

    @Test
    void theRemoteGhRepoSetDefaultChoseIsTheOneItPrefers() {
        Map<RemoteName, RemoteUrl> urls = HashMap.of(
                new RemoteName("origin"), new RemoteUrl("https://codeberg.org/owner/repo.git"),
                new RemoteName("hub"), new RemoteUrl("https://github.com/owner/repo.git"));
        Env chosen = new Env(StubGit.of().withRemotes(new StubRemotes() {
                    @Override
                    public Either<GitError, Map<RemoteName, RemoteUrl>> urls() {
                        return Either.right(urls);
                    }

                    @Override
                    public Either<GitError, Map<RemoteName, String>> setting(String key) {
                        return Either.right(
                                key.equals("gh-resolved")
                                        ? HashMap.of(new RemoteName("hub"), "base")
                                        : HashMap.empty());
                    }
                }))
                .with(GitHubServices.GH, StubGitHub.of());

        Msg detected = new DetectGitHub("gh").run(chosen);
        assertEquals(List.of("hub"), claimed(detected));
        assertEquals(
                Option.of(new RemoteName("hub")),
                ((FeatureMsg.Claimed) detected).claim().preferred());
    }

    @Test
    void ghIsAskedWhereItIsLoggedInOnlyForAHostThatDoesNotSayItIsGitHubs() {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        GitHub counting = StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Either<GhError, List<GhLogin>> logins() {
                asked.incrementAndGet();
                return Either.right(List.empty());
            }
        });

        detect("https://github.com/owner/repo.git", counting);
        assertEquals(0, asked.get());
        detect("https://git.acme.com/owner/repo.git", counting);
        assertEquals(1, asked.get());
    }

    @Test
    void aHostGhIsLoggedInToIsGitHubs() {
        GitHub loggedIn = StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Either<GhError, List<GhLogin>> logins() {
                return Either.right(List.of(new GhLogin("git.acme.com", "ann", true, true)));
            }
        });
        assertEquals(List.of("origin"), claimed(detect("https://git.acme.com/owner/repo.git", loggedIn)));
    }

    @Test
    void withoutGhItIsNot() {
        GitHub missing = StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Either<GhError, String> version(String executable) {
                return Either.left(new GhError.GhUnavailable(new java.io.IOException("no gh")));
            }
        });
        assertEquals(List.empty(), claimed(detect("https://github.com/owner/repo.git", missing)));
    }

    private static List<String> claimed(Msg detected) {
        return ((FeatureMsg.Claimed) detected)
                .claim()
                .claimed()
                .keySet()
                .toList()
                .map(RemoteName::value)
                .sorted();
    }

    private static Msg detect(String url, GitHub gh) {
        return new DetectGitHub("gh").run(env(HashMap.of(new RemoteName("origin"), new RemoteUrl(url)), gh));
    }

    private static Env env(Map<RemoteName, RemoteUrl> urls, GitHub gh) {
        return new Env(StubGit.of().withRemotes(new StubRemotes() {
                    @Override
                    public Either<GitError, Map<RemoteName, RemoteUrl>> urls() {
                        return Either.right(urls);
                    }
                }))
                .with(GitHubServices.GH, gh);
    }
}
