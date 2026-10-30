package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.GitCommand.reading;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.Locale;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads what a pull request on GitHub changes through the git command line tool, as GitHub shows it: its head commit
 * against the merge base of that and its base commit, {@code <base>...<head>} (ADR 55). Its commits are fetched
 * first when the repository does not have them.
 */
final class CliPullRequest {

    private static final List<String> DIFF =
            List.of("diff", "--no-color", "--no-ext-diff").appendAll(CliDiff.PREFIXES);

    private CliPullRequest() {}

    /**
     * The files the pull request of {@code commits} changes, with renames found.
     *
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<ProcessError, List<CommitFile>> files(
            Path worktree, PullRequestCommits commits, Map<String, String> credentials) {
        return fetched(worktree, commits, credentials)
                .flatMap(done -> GitProcess.read(
                        worktree,
                        reading(List.of("diff", "--name-status", "-z", "-M").append(range(commits))),
                        CliShow::parseFiles));
    }

    /**
     * What the pull request of {@code commits} changes in {@code files}, read with {@code options}. Long output is
     * cut off. Without files, nothing is read: git would show every file. Its commits were fetched, if they had to
     * be, when its files were read.
     */
    static Either<ProcessError, DiffText> fileChanges(
            Path worktree, PullRequestCommits commits, List<CommitFile> files, DiffOptions options) {
        if (files.isEmpty()) {
            return Either.right(DiffText.of(""));
        }
        return GitProcess.read(
                worktree,
                reading(DIFF.append("-M")
                                .appendAll(CliDiff.flags(options))
                                .append(range(commits))
                                .append("--")
                                .appendAll(files.flatMap(CommitFile::paths)))
                        .withLiteralPaths(),
                output -> Either.right(CliDiff.truncated(output)));
    }

    private static String range(PullRequestCommits commits) {
        return GitArgs.symmetric(new Revision.Commit(commits.base()), new Revision.Commit(commits.head()));
    }

    /**
     * Fetches the head and the base of the pull request when the repository lacks either commit, from the remote of
     * its repository: {@code refs/pull/<n>/head}, which GitHub keeps for every pull request, also from a fork, and
     * its base commit, by its sha, which the base branch may have moved on from. It writes no ref, not even
     * {@code FETCH_HEAD}: with {@code --refmap=}, no fetch refspec of the remote applies, so git does not update the
     * remote-tracking branch of what it fetches by name either, and it needs no guard of the remote refs (ADR 27).
     * Without a remote of its repository, nothing is fetched: reading the diff then fails as git says.
     */
    private static Either<ProcessError, Boolean> fetched(
            Path worktree, PullRequestCommits commits, Map<String, String> credentials) {
        return has(worktree, commits.head())
                .flatMap(head -> head ? has(worktree, commits.base()) : Either.right(false))
                .flatMap(both -> both
                        ? Either.right(true)
                        : CliRemote.remoteUrls(worktree)
                                .flatMap(urls -> remoteOf(urls, commits.repository())
                                        .map(remote -> CliRemote.run(worktree, fetch(remote, commits), credentials)
                                                .<ProcessError>mapLeft(GitFailure::error)
                                                .map(ran -> true))
                                        .getOrElse(() -> Either.right(false))));
    }

    private static GitCommand fetch(RemoteName remote, PullRequestCommits commits) {
        return git(
                "fetch",
                "--quiet",
                "--no-write-fetch-head",
                "--refmap=",
                GitArgs.remote(remote),
                "refs/pull/" + commits.number() + "/head",
                commits.base().value());
    }

    /**
     * Whether the repository has the commit {@code sha}.
     */
    private static Either<ProcessError, Boolean> has(Path worktree, Sha sha) {
        return GitProcess.ask(
                        worktree,
                        reading(
                                "rev-parse",
                                "--verify",
                                "--quiet",
                                "--end-of-options",
                                GitArgs.revision(new Revision.Commit(sha)) + "^{commit}"))
                .map(Option::isDefined)
                .mapLeft(GitFailure::error);
    }

    /**
     * The remote whose URL names the repository {@code repository}, {@code owner/name}: whose path ends in it, with or
     * without {@code .git}, ignoring case, as GitHub does; the first by name when several do.
     */
    static Option<RemoteName> remoteOf(Map<String, String> urls, String repository) {
        String wanted = "/" + repository.toLowerCase(Locale.ROOT);
        return urls.toList()
                .sortBy(remote -> remote._1)
                .find(remote -> path(remote._2).endsWith(wanted))
                .map(remote -> new RemoteName(remote._1));
    }

    /**
     * The path of the remote URL {@code url}, lower case, without a {@code .git} or {@code /} at its end, and with the
     * {@code :} of an scp-like URL ({@code git@host:owner/name}) read as a {@code /}.
     */
    private static String path(String url) {
        String lower = url.strip().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        String bare = lower.endsWith(".git") ? lower.substring(0, lower.length() - ".git".length()) : lower;
        return bare.contains("://") ? bare : bare.replace(':', '/');
    }
}
