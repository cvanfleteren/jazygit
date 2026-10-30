package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
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
        PullRequestCommits commits = new PullRequestCommits(1, "owner/repo", new BranchName("main"), base, head);

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
    void theCommitsOfAPullRequestAreFetchedFromTheRemoteOfItsRepositoryWithoutARef() throws Exception {
        Path origin = dir.resolve("owner").resolve("repo.git");
        Path repo = dir.resolve("repo");
        Path fork = dir.resolve("fork");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        Sha base = commit(repo, "a.txt", "a\n", "first");
        git(repo, "push", "-q", "origin", "main");
        // Someone else's branch reaches the repository only as the head of the pull request, as from a fork.
        git(dir, "clone", "-q", origin.toString(), fork.toString());
        Sha head = commit(fork, "b.txt", "b\n", "theirs");
        git(fork, "push", "-q", "origin", "HEAD:refs/pull/7/head");
        String refs = git(repo, "for-each-ref");
        PullRequestCommits commits = new PullRequestCommits(7, "Owner/Repo", new BranchName("main"), base, head);

        assertEquals(
                List.of(new CommitFile("b.txt", ChangeType.ADDED)),
                CliPullRequest.files(repo, commits, HashMap.empty()).get());
        assertEquals(refs, git(repo, "for-each-ref"));
        assertFalse(Files.exists(repo.resolve(".git").resolve("FETCH_HEAD")));
    }

    @Test
    void withoutARemoteOfItsRepositoryNothingIsFetchedAndTheReadFails() throws Exception {
        Path repo = dir.resolve("repo");
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", dir.resolve("elsewhere.git").toString());
        Sha base = commit(repo, "a.txt", "a\n", "first");
        Sha missing = new Sha("0123456789012345678901234567890123456789");
        PullRequestCommits commits = new PullRequestCommits(7, "owner/repo", new BranchName("main"), base, missing);

        assertTrue(CliPullRequest.files(repo, commits, HashMap.empty()).isLeft());
    }

    @Test
    void theRemoteOfARepositoryIsTheOneWhoseUrlNamesIt() {
        HashMap<String, String> urls = HashMap.of(
                "upstream", "git@github.com:Owner/Repo.git",
                "origin", "https://github.com/me/repo",
                "mirror", "ssh://git@github.com/owner/repo/",
                "other", "https://github.com/owner/repository.git");

        assertEquals(Option.of(new RemoteName("mirror")), CliPullRequest.remoteOf(urls, "owner/repo"));
        assertEquals(Option.of(new RemoteName("origin")), CliPullRequest.remoteOf(urls, "me/repo"));
        assertEquals(Option.none(), CliPullRequest.remoteOf(urls, "owner/rep"));
        assertEquals(
                Option.of(new RemoteName("upstream")), CliPullRequest.remoteOf(urls.remove("mirror"), "owner/repo"));
    }
}
