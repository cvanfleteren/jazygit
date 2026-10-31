package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.GitCommand.reading;

import io.vavr.Tuple2;
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
 * first when the repository does not have them. A branch is read against its base the same way, without a fetch
 * (ADR 61).
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
        return fetched(worktree, commits, credentials).flatMap(done -> files(worktree, commits.base(), commits.head()));
    }

    /**
     * The files {@code head} changes against the merge base of it and {@code base}, with renames found.
     */
    static Either<ProcessError, List<CommitFile>> files(Path worktree, Sha base, Sha head) {
        return GitProcess.read(
                worktree,
                reading(List.of("diff", "--name-status", "-z", "-M").append(range(base, head))),
                CliShow::parseFiles);
    }

    /**
     * What the pull request of {@code commits} changes in {@code files}, read with {@code options}. Long output is
     * cut off. Without files, nothing is read: git would show every file. Its commits were fetched, if they had to
     * be, when its files were read.
     */
    static Either<ProcessError, DiffText> fileChanges(
            Path worktree, PullRequestCommits commits, List<CommitFile> files, DiffOptions options) {
        return fileChanges(worktree, commits.base(), commits.head(), files, options);
    }

    /**
     * What {@code head} changes in {@code files} against the merge base of it and {@code base}, read with
     * {@code options}. Long output is cut off; without files, nothing is read.
     */
    static Either<ProcessError, DiffText> fileChanges(
            Path worktree, Sha base, Sha head, List<CommitFile> files, DiffOptions options) {
        if (files.isEmpty()) {
            return Either.right(DiffText.of(""));
        }
        return GitProcess.read(
                worktree,
                reading(DIFF.append("-M")
                                .appendAll(CliDiff.flags(options))
                                .append(range(base, head))
                                .append("--")
                                .appendAll(files.flatMap(CommitFile::paths)))
                        .withLiteralPaths(),
                output -> Either.right(CliDiff.truncated(output)));
    }

    private static String range(Sha base, Sha head) {
        return GitArgs.symmetric(new Revision.Commit(base), new Revision.Commit(head));
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
                                .flatMap(urls -> remoteOf(urls, commits.repository(), commits.host())
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
     * The remote whose URL names the repository {@code repository}, {@code owner/name}, on {@code host}: whose path
     * ends in it, with or without {@code .git}, ignoring case, as GitHub does. A mirror of the same name on another
     * host does not have the pull requests, so one on {@code host} is taken first; when none is, as when an ssh alias
     * names it, the first whose path names it. The first by name when several do.
     */
    static Option<RemoteName> remoteOf(Map<String, String> urls, String repository, String host) {
        String wanted = "/" + repository.toLowerCase(Locale.ROOT);
        List<Tuple2<String, String>> named = urls.toList()
                .sortBy(remote -> remote._1)
                .filter(remote -> path(remote._2).endsWith(wanted));
        return named.find(remote -> host(remote._2).equals(host.toLowerCase(Locale.ROOT)))
                .orElse(named::headOption)
                .map(remote -> new RemoteName(remote._1));
    }

    /**
     * The host of the remote URL {@code url}, lower case, without who logs in or the port: that of
     * {@code scheme://user@host:port/path} or of an scp-like {@code user@host:path}; empty for a path on disk.
     */
    private static String host(String url) {
        String lower = url.strip().toLowerCase(Locale.ROOT);
        String rest = lower.contains("://") ? lower.substring(lower.indexOf("://") + "://".length()) : lower;
        String authority = rest.split("[/:]", 2)[0];
        return authority.substring(authority.lastIndexOf('@') + 1);
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
