package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * What a pull request changes, as GitHub shows it: its head against the merge base of its head and its base, its
 * commits fetched when the repository lacks them (ADR 55).
 */
class CliPullRequestTest {

    @TempDir
    Path dir;

    private static String git(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(
                java.util.List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }

    private static Sha commit(Path in, String file, String content, String message) throws Exception {
        Files.writeString(in.resolve(file), content);
        git(in, "add", file);
        git(in, "commit", "-q", "-m", message);
        return Sha.parse(git(in, "rev-parse", "HEAD")).get();
    }

    @Test
    void theChangesAreThoseOfTheHeadSinceItLeftTheBaseAsGitHubShowsThem() throws Exception {
        Path repo = dir.resolve("repo");
        git(dir, "init", "-q", "-b", "main", repo.toString());
        commit(repo, "a.txt", "a\n", "first");
        git(repo, "checkout", "-q", "-b", "feature");
        Sha head = commit(repo, "b.txt", "b\n", "feature");
        git(repo, "checkout", "-q", "main");
        // The base moved on after the branch left it: that is no change of the pull request.
        Sha base = commit(repo, "a.txt", "a\nmore\n", "main moved on");
        PullRequestCommits commits = new PullRequestCommits(1, "owner/repo", "", base, head);

        assertEquals(
                List.of(new CommitFile("b.txt", ChangeType.ADDED)),
                CliPullRequest.files(repo, commits, HashMap.empty()).get());
        String diff = CliPullRequest.fileChanges(
                        repo, commits, List.of(new CommitFile("b.txt", ChangeType.ADDED)), DiffOptions.DEFAULT)
                .get()
                .text();
        assertTrue(diff.contains("diff --git a/b.txt b/b.txt"), diff);
        assertTrue(diff.contains("+b"), diff);
        assertFalse(diff.contains("more"), diff);
    }

    @Test
    void aBranchIsReadAgainstItsBaseAsAPullRequestOfItWouldBeWithoutARemote() throws Exception {
        Path repo = dir.resolve("repo");
        git(dir, "init", "-q", "-b", "main", repo.toString());
        commit(repo, "a.txt", "a\n", "first");
        git(repo, "checkout", "-q", "-b", "feature");
        Sha tip = commit(repo, "b.txt", "b\n", "feature");
        git(repo, "checkout", "-q", "main");
        Sha base = commit(repo, "a.txt", "a\nmore\n", "main moved on");

        assertEquals(
                List.of(new CommitFile("b.txt", ChangeType.ADDED)),
                CliPullRequest.files(repo, base, tip).get());
        String diff = CliPullRequest.fileChanges(
                        repo, base, tip, List.of(new CommitFile("b.txt", ChangeType.ADDED)), DiffOptions.DEFAULT)
                .get()
                .text();
        assertTrue(diff.contains("+b"), diff);
        assertFalse(diff.contains("more"), diff);
    }

    @Test
    void theCommitsOfAPullRequestAreFetchedFromTheRemoteOfItsRepositoryWithoutARef() throws Exception {
        Path origin = dir.resolve("owner").resolve("repo.git");
        Path repo = dir.resolve("repo");
        Path fork = dir.resolve("fork");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        // A common setup: the remote fetches the heads of pull requests too. Without --refmap=, git would write
        // origin/pr/7 as it fetches refs/pull/7/head.
        git(repo, "config", "--add", "remote.origin.fetch", "+refs/pull/*/head:refs/remotes/origin/pr/*");
        Sha base = commit(repo, "a.txt", "a\n", "first");
        git(repo, "push", "-q", "origin", "main");
        // Someone else's branch reaches the repository only as the head of the pull request, as from a fork.
        git(dir, "clone", "-q", origin.toString(), fork.toString());
        Sha head = commit(fork, "b.txt", "b\n", "theirs");
        git(fork, "push", "-q", "origin", "HEAD:refs/pull/7/head");
        String refs = git(repo, "for-each-ref");
        PullRequestCommits commits = new PullRequestCommits(7, "Owner/Repo", "", base, head);

        assertEquals(
                List.of(new CommitFile("b.txt", ChangeType.ADDED)),
                CliPullRequest.files(repo, commits, HashMap.empty()).get());
        assertEquals(refs, git(repo, "for-each-ref"));
        assertFalse(Files.exists(repo.resolve(".git").resolve("FETCH_HEAD")));
    }

    @Test
    void aBaseThatMovedOnIsFetchedByItsShaWithoutMovingTheRemoteTrackingBranch() throws Exception {
        Path origin = dir.resolve("owner").resolve("repo.git");
        Path repo = dir.resolve("repo");
        Path other = dir.resolve("other");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        // A common setup: the remote fetches the heads of pull requests too. Without --refmap=, git would write
        // origin/pr/7 as it fetches refs/pull/7/head.
        git(repo, "config", "--add", "remote.origin.fetch", "+refs/pull/*/head:refs/remotes/origin/pr/*");
        commit(repo, "a.txt", "a\n", "first");
        git(repo, "push", "-q", "-u", "origin", "main");
        git(dir, "clone", "-q", origin.toString(), other.toString());
        // The base the pull request was read at, which the repository has not fetched, and which main then moved on
        // from: fetching main would not bring it, and would move origin/main.
        Sha base = commit(other, "a.txt", "a\nbase\n", "base");
        git(other, "push", "-q", "origin", "main");
        git(other, "checkout", "-q", "-b", "feature");
        Sha head = commit(other, "b.txt", "b\n", "theirs");
        git(other, "push", "-q", "origin", "HEAD:refs/pull/7/head");
        git(other, "checkout", "-q", "main");
        commit(other, "a.txt", "a\nbase\nlater\n", "later");
        git(other, "push", "-q", "origin", "main");
        String refs = git(repo, "for-each-ref");
        PullRequestCommits commits = new PullRequestCommits(7, "owner/repo", "", base, head);

        assertEquals(
                List.of(new CommitFile("b.txt", ChangeType.ADDED)),
                CliPullRequest.files(repo, commits, HashMap.empty()).get());
        assertEquals(refs, git(repo, "for-each-ref"));
    }

    @Test
    void withoutARemoteOfItsRepositoryNothingIsFetchedAndTheReadFails() throws Exception {
        // The remote has the head of the pull request, but its URL names another repository.
        Path elsewhere = dir.resolve("other").resolve("thing.git");
        Path repo = dir.resolve("repo");
        Path fork = dir.resolve("fork");
        git(dir, "init", "-q", "--bare", "-b", "main", elsewhere.toString());
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", elsewhere.toString());
        Sha base = commit(repo, "a.txt", "a\n", "first");
        git(repo, "push", "-q", "origin", "main");
        git(dir, "clone", "-q", elsewhere.toString(), fork.toString());
        Sha head = commit(fork, "b.txt", "b\n", "theirs");
        git(fork, "push", "-q", "origin", "HEAD:refs/pull/7/head");
        PullRequestCommits commits = new PullRequestCommits(7, "owner/repo", "", base, head);

        assertTrue(CliPullRequest.files(repo, commits, HashMap.empty()).isLeft());
        assertThrows(Exception.class, () -> git(repo, "cat-file", "-e", head.value()));
    }

    @Test
    void theRemoteOfARepositoryIsTheOneWhoseUrlNamesIt() {
        HashMap<String, String> urls = HashMap.of(
                "upstream", "git@github.com:Owner/Repo.git",
                "origin", "https://github.com/me/repo",
                "mirror", "ssh://git@github.com/owner/repo/",
                "other", "https://github.com/owner/repository.git");

        assertEquals(Option.of(new RemoteName("mirror")), CliPullRequest.remoteOf(urls, "owner/repo", "github.com"));
        assertEquals(Option.of(new RemoteName("origin")), CliPullRequest.remoteOf(urls, "me/repo", "github.com"));
        assertEquals(Option.none(), CliPullRequest.remoteOf(urls, "owner/rep", "github.com"));
        assertEquals(
                Option.of(new RemoteName("upstream")),
                CliPullRequest.remoteOf(urls.remove("mirror"), "owner/repo", "github.com"));
    }

    @Test
    void aRemoteOnTheHostOfTheRepositoryComesBeforeAMirrorElsewhere() {
        HashMap<String, String> urls = HashMap.of(
                "backup", "https://gitlab.com/owner/repo.git",
                "origin", "git@github.com:owner/repo.git",
                "work", "https://user@ghe.example.com:8443/owner/repo");

        assertEquals(Option.of(new RemoteName("origin")), CliPullRequest.remoteOf(urls, "owner/repo", "github.com"));
        assertEquals(Option.of(new RemoteName("work")), CliPullRequest.remoteOf(urls, "owner/repo", "GHE.example.com"));
        // None on the host, as when an ssh alias names it: the first by name whose path names the repository.
        assertEquals(Option.of(new RemoteName("backup")), CliPullRequest.remoteOf(urls, "owner/repo", "gh-work"));
    }
}
