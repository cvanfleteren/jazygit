package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.PushRefused;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.PushDefault;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The {@link Remotes} of a repository: talks to the remotes through the {@code git} command line tool, with the
 * user's own configuration and the credentials it was given, in the background: nothing may ask the user anything.
 * The files of a pull request are fetched through {@link CliPullRequest}, which runs git through {@link #run} and
 * reads the URLs of the remotes through {@link #remoteUrls(Path)}.
 */
final class CliRemotes implements Remotes {

    /**
     * The remote git names for this repository itself, as for a branch created with {@code --track} from a local one.
     */
    static final RemoteName THIS_REPOSITORY = new RemoteName(".");

    // A first fetch of a big repository takes a while; a fetch that is still running is not started again.
    // Talking to a remote takes a while for a big repository, or over a slow connection.
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final String REMOTE_KEY = "remote.";
    private static final String SKIP_FETCH_ALL_KEY = ".skipfetchall";
    private static final String URL_KEY = ".url";
    private static final String PUSH_DEFAULT = "push.default";
    private static final String SIMPLE = "simple";

    private final Path worktree;
    // Asked each time git talks to a remote: the environment variables that tell the credential helper which account
    // to use.
    private final Supplier<Map<String, String>> credentials;

    CliRemotes(Path worktree, Supplier<Map<String, String>> credentials) {
        this.worktree = worktree;
        this.credentials = credentials;
    }

    /**
     * The remotes {@code git fetch --all} would fetch: all of them but those with {@code skipFetchAll} set.
     */
    @Override
    public Either<GitError, List<RemoteName>> list() {
        return GitProcess.read(worktree, GitCommand.reading("remote"), raw -> Either.right(names(raw)))
                .flatMap(remotes -> skippedByFetchAll().map(remotes::removeAll).mapLeft(GitFailure::error))
                .map(remotes -> remotes.flatMap(RemoteName::parse));
    }

    private static List<String> names(String raw) {
        return GitOutput.nonBlankLines(raw).map(String::strip);
    }

    /**
     * The remotes with {@code remote.<name>.skipFetchAll} set to true.
     */
    private Either<GitFailure, List<String>> skippedByFetchAll() {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading("config", "--bool", "--get-regexp", "^remote\\..*\\.skipfetchall$"))
                .map(answer -> answer.map(output -> skipped(output.stdout())).getOrElse(List.empty()));
    }

    /**
     * Parses {@code remote.<name>.skipfetchall <true|false>} lines into the names set to true.
     */
    static List<String> skipped(String raw) {
        return GitOutput.nonBlankLines(raw)
                .flatMap(line -> GitOutput.fields(line, " ", 2).toOption())
                .filter(fields -> fields.last().strip().equals("true"))
                .flatMap(fields -> GitOutput.after(fields.head(), REMOTE_KEY))
                .filter(rest -> rest.endsWith(SKIP_FETCH_ALL_KEY))
                .map(rest -> rest.substring(0, rest.length() - SKIP_FETCH_ALL_KEY.length()))
                .filter(name -> !name.isEmpty());
    }

    @Override
    public Either<GitError, Map<String, String>> urls() {
        return remoteUrls(worktree);
    }

    /**
     * The URL of each remote, by its name. Only looked up, so not logged.
     */
    static Either<GitError, Map<String, String>> remoteUrls(Path worktree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(worktree, GitCommand.reading("config", "--get-regexp", "^remote\\..*\\.url$"))
                .map(answer -> answer.map(output -> parseUrls(output.stdout())).getOrElse(HashMap.empty()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Parses {@code remote.<name>.url <url>} lines into the URL of each remote; the first, for a remote with
     * several.
     */
    static Map<String, String> parseUrls(String raw) {
        return GitOutput.nonBlankLines(raw)
                .flatMap(line -> GitOutput.fields(line, " ", 2).toOption())
                .flatMap(fields -> GitOutput.after(fields.head(), REMOTE_KEY)
                        .filter(rest -> rest.endsWith(URL_KEY))
                        .map(rest -> rest.substring(0, rest.length() - URL_KEY.length()))
                        .filter(name -> !name.isEmpty())
                        .map(name -> Tuple.of(name, fields.last().strip())))
                .reverse()
                .toMap(url -> url);
    }

    /**
     * Pushes {@code name}, which tracks {@code upstream}, where {@code push.default} says: to {@code upstream}'s
     * remote, on the branch {@link #target} names.
     *
     * @return the command lines that were run, or why git failed; {@link PushRefused} when {@code push.default}
     *     pushes the branch nowhere, or has a value git does not know, before anything is pushed
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> push(BranchName name, RemoteBranch upstream, PushMode mode) {
        return CliConfig.effective(worktree, PUSH_DEFAULT)
                .mapLeft(error -> new GitFailure(error, List.empty()))
                .map(setting -> setting.getOrElse(SIMPLE))
                .flatMap(setting -> PushDefault.parse(setting)
                        .flatMap(pushDefault -> target(pushDefault, name, upstream))
                        .toEither(() -> new GitFailure(new PushRefused(setting, upstream), List.empty())))
                .flatMap(target -> run(
                        worktree,
                        git(List.of("push")
                                .appendAll(
                                        mode == PushMode.FORCE_WITH_LEASE
                                                ? List.of("--force-with-lease")
                                                : List.empty())
                                .append(GitArgs.remote(target.remote()))
                                .append(refspec(name, target))),
                        credentials.get()));
    }

    /**
     * Where {@code push.default} pushes {@code name}, which tracks {@code upstream}, as {@code git push} would push
     * it when checked out: on {@code upstream}'s remote, to the branch of the same name, or to {@code upstream} for
     * {@code upstream}; none for {@code nothing}, and for {@code simple} when {@code upstream} has another name.
     * {@code matching}, which pushes every branch of the same name, pushes this one to its name.
     */
    static Option<RemoteBranch> target(PushDefault pushDefault, BranchName name, RemoteBranch upstream) {
        RemoteBranch sameName = new RemoteBranch(upstream.remote(), name);
        return switch (pushDefault) {
            case CURRENT, MATCHING -> Option.of(sameName);
            case UPSTREAM -> Option.of(upstream);
            case NOTHING -> Option.none();
            case SIMPLE -> Option.when(upstream.branch().equals(name), sameName);
        };
    }

    /**
     * Runs {@code git push --set-upstream <remote> refs/heads/<name>:refs/heads/<branch>}: pushes {@code name} to
     * {@code upstream}, which it then tracks. Git only sets the upstream when the push succeeds.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> pushSettingUpstream(BranchName name, RemoteBranch upstream) {
        return run(
                worktree,
                git("push", "--set-upstream", GitArgs.remote(upstream.remote()), refspec(name, upstream)),
                credentials.get());
    }

    /**
     * Runs {@code git push --force-with-lease --force-if-includes --atomic --set-upstream <remote>
     * refs/heads/<local>:refs/heads/<branch>…}: GitHub takes all of them or none, and refuses one that is no longer
     * where it was last fetched, or that was fetched where the local branch never was, as when a fetch in the
     * background brought in what an agent pushed.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> pushAll(
            RemoteName remote, List<Tuple2<BranchName, BranchName>> branches) {
        return run(
                worktree,
                git(List.of(
                                "push",
                                "--force-with-lease",
                                "--force-if-includes",
                                "--atomic",
                                "--set-upstream",
                                GitArgs.remote(remote))
                        .appendAll(
                                branches.map(pushed -> refspec(pushed._1(), new RemoteBranch(remote, pushed._2()))))),
                credentials.get());
    }

    /**
     * What to push {@code name} as: {@code refs/heads/<name>:refs/heads/<branch>}, in full, so that a tag of the same
     * name is not pushed instead (ADR 40).
     */
    private static String refspec(BranchName name, RemoteBranch target) {
        return GitArgs.refspec(new BranchRef.Local(name), new BranchRef.Local(target.branch()));
    }

    /**
     * Runs {@code git pull --no-edit}: pulls the checked out branch from its upstream, merging or rebasing as
     * git's {@code pull.rebase} and {@code pull.ff} say. A merge commit gets git's own message instead of an
     * editor, and with {@code pull.rebase=interactive} the todo list is taken as it is. A pull that stops on
     * conflicts is left so, for the user to resolve them; a rebase that stops otherwise is aborted again. One that
     * git refuses to start, as the repository is in the middle of something else, leaves that alone.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> pull() {
        return toRemote(
                        worktree,
                        git("pull", "--no-edit").withEnvironment(HashMap.of("GIT_SEQUENCE_EDITOR", ":")),
                        credentials.get())
                .flatMap(pull ->
                        Unfinished.runOrUndo(worktree, pull, left -> Option.when(left.rebasing(), CliRebase.ABORT)));
    }

    @Override
    public Either<GitFailure, Boolean> fetch(RemoteName remote) {
        return fetch(worktree, remote, Option.of(System.getenv("GIT_SSH_COMMAND")), credentials.get());
    }

    /**
     * Runs {@code git fetch --porcelain --prune --no-write-fetch-head <remote>}, and tells whether it changed a
     * remote-tracking branch: {@code --porcelain} prints a line for each ref it updated, created or pruned, and nothing
     * when it changed none, so that the branches need not be read again (ADR 58). It always prunes, whatever
     * {@code fetch.prune} says: the remote-tracking branches of branches deleted on the remote go, so git marks the
     * local branches that followed them as gone. It leaves {@code FETCH_HEAD} alone, which nothing reads after it, but
     * a pull does after its own fetch. Git's own prompts are already off; ssh would still ask for a passphrase or about
     * an unknown host on the terminal, so it runs in batch mode.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure, Boolean> fetch(
            Path worktree, RemoteName remote, Option<String> environment, Map<String, String> credentials) {
        // Not --quiet, which keeps --porcelain from printing too; --porcelain prints no progress either.
        return toRemote(
                        worktree,
                        git("fetch", "--porcelain", "--prune", "--no-write-fetch-head", GitArgs.remote(remote)),
                        environment,
                        credentials)
                .flatMap(fetch -> GitProcess.run(worktree, fetch))
                .map(fetched -> !fetched.stdout().isBlank());
    }

    /**
     * Runs {@code git fetch <remote> refs/heads/<upstream>:refs/heads/<name>}: fast-forwards {@code name}, which
     * is not checked out, to {@code upstream}, the branch it tracks; the remote is {@code .} for a local one. Git
     * refuses when that is not a fast-forward.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> fastForward(BranchName name, BranchRef upstream) {
        return switch (upstream) {
            case BranchRef.Local(BranchName branch) -> fastForward(name, THIS_REPOSITORY, branch);
            case BranchRef.Remote(RemoteBranch remote) -> fastForward(name, remote.remote(), remote.branch());
        };
    }

    private Either<GitFailure, List<ExecutedCommand>> fastForward(
            BranchName name, RemoteName remote, BranchName branch) {
        return run(
                worktree,
                git(
                        "fetch",
                        GitArgs.remote(remote),
                        GitArgs.refspec(new BranchRef.Local(branch), new BranchRef.Local(name))),
                credentials.get());
    }

    /**
     * Deletes the branch {@code name} where {@code scope} says: locally, and/or {@code remote}, the branch it is
     * pushed to. With both, the remote one goes first, so a failure leaves the local branch in place.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope) {
        return GitFailure.sequence(
                () -> scope.remote() ? deleteRemoteBranch(remote) : GitFailure.nothing(),
                () -> scope.local() ? CliBranches.deleteLocal(worktree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git push <remote> --delete refs/heads/<branch>}: the branch is named in full, as it is on the
     * remote, so a tag of the same name there neither makes git refuse nor is deleted instead (ADR 40).
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return run(
                worktree,
                git(
                        "push",
                        GitArgs.remote(remote.remote()),
                        "--delete",
                        GitArgs.revision(new BranchRef.Local(remote.branch()))),
                credentials.get());
    }

    /**
     * Fetches {@code upstream}'s remote, so that the rename starts from what is there now, then runs
     * {@code git push --atomic --force-with-lease=refs/heads/<branch>: --force-with-lease=refs/heads/<old> <remote>
     * refs/remotes/<remote>/<old>:refs/heads/<branch> :refs/heads/<old>}: it pushes the commit the remote-tracking
     * branch has, not the local branch, and git refuses all of it when the new name is taken or the old one moved.
     * An upstream the fetch pruned is not pushed at all. Last, {@code git config branch.<branch>.merge
     * refs/heads/<branch>} has {@code branch} follow the renamed one; its remote stays. Not {@code git branch
     * --set-upstream-to}, which needs the remote-tracking branch, which the push does not make when the remote's fetch
     * refspec leaves it out. That step is no part of the rename on the remote, which happened when it fails.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> renameUpstream(BranchName branch, RemoteBranch upstream) {
        BranchRef.Local old = new BranchRef.Local(upstream.branch());
        BranchRef.Local target = new BranchRef.Local(branch);
        return GitFailure.sequence(
                        () -> run(
                                worktree,
                                git("fetch", "--prune", "--no-write-fetch-head", GitArgs.remote(upstream.remote())),
                                credentials.get()),
                        () -> stillOnRemote(upstream, branch),
                        () -> run(
                                worktree,
                                git(
                                        "push",
                                        "--atomic",
                                        GitArgs.leaseAbsent(target),
                                        GitArgs.lease(old),
                                        GitArgs.remote(upstream.remote()),
                                        GitArgs.refspec(upstream.tracking(), target),
                                        GitArgs.deletion(old)),
                                credentials.get()))
                .mapLeft(failure -> failure.error() instanceof GitError.UpstreamNotOnRemote
                        ? failure
                        : new GitFailure(
                                new GitError.UpstreamNotRenamed(upstream, branch, failure.error()), failure.commands()))
                .flatMap(renamed -> GitProcess.log(
                                worktree,
                                git(
                                        "config",
                                        "--local",
                                        "branch." + GitArgs.name(branch) + ".merge",
                                        GitArgs.revision(target)))
                        .map(renamed::appendAll)
                        .mapLeft(failure -> failure.after(renamed)));
    }

    /**
     * Whether {@code upstream} is still on its remote, as its remote-tracking branch tells after a fetch; when not,
     * {@link GitError.UpstreamNotOnRemote}. Only looked up, so not logged.
     */
    private Either<GitFailure, List<ExecutedCommand>> stillOnRemote(RemoteBranch upstream, BranchName branch) {
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading("rev-parse", "--verify", "--quiet", GitArgs.revision(upstream.tracking())))
                .flatMap(answer -> answer.isDefined()
                        ? GitFailure.nothing()
                        : Either.left(
                                new GitFailure(new GitError.UpstreamNotOnRemote(upstream, branch), List.empty())));
    }

    @Override
    public Either<GitError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits) {
        return CliPullRequest.files(worktree, commits, credentials.get());
    }

    /**
     * Runs {@code command}, which talks to a remote, for its effect.
     *
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure, List<ExecutedCommand>> run(
            Path worktree, GitCommand command, Map<String, String> credentials) {
        return toRemote(worktree, command, credentials).flatMap(remote -> GitProcess.log(worktree, remote));
    }

    /**
     * {@code command}, which talks to a remote, run so that ssh fails instead of asking for a passphrase or about
     * an unknown host on the terminal, which the TUI owns; and with the time the network takes.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure, GitCommand> toRemote(
            Path worktree, GitCommand command, Option<String> environment, Map<String, String> credentials) {
        return configuredSsh(worktree, environment)
                .map(ssh -> command.withEnvironment(credentials.put("GIT_SSH_COMMAND", batchMode(ssh)))
                        .withTimeout(TIMEOUT));
    }

    /**
     * {@code command}, which talks to a remote, as {@link #toRemote(Path, GitCommand, Option, Map)} runs it, with
     * the ssh command of this process's environment.
     */
    static Either<GitFailure, GitCommand> toRemote(Path worktree, GitCommand command, Map<String, String> credentials) {
        return toRemote(worktree, command, Option.of(System.getenv("GIT_SSH_COMMAND")), credentials);
    }

    /**
     * The ssh command git would run, or none when it runs plain {@code ssh}: {@code GIT_SSH_COMMAND}, else
     * {@code core.sshCommand}. Only looked up, so not logged.
     */
    private static Either<GitFailure, Option<String>> configuredSsh(Path worktree, Option<String> environment) {
        Option<String> set = environment.filter(command -> !command.isBlank());
        // git config --get exits with 1, saying nothing, when the key is not set.
        return set.isDefined()
                ? Either.right(set)
                : GitProcess.ask(worktree, GitCommand.reading("config", "--get", "core.sshCommand"))
                        .map(answer ->
                                answer.map(output -> output.stdout().strip()).filter(command -> !command.isEmpty()));
    }

    /**
     * {@code ssh}, the configured command, run so that it fails instead of asking anything.
     */
    static String batchMode(Option<String> ssh) {
        return ssh.getOrElse("ssh") + " -o BatchMode=yes";
    }
}
