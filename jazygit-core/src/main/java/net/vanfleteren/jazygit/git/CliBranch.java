package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.model.BranchRef.HEADS;
import static net.vanfleteren.jazygit.git.model.BranchRef.REMOTES;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Lists, renames and deletes branches through the {@code git} command line tool. The branches are read through
 * {@code git for-each-ref}, in one call for all of them.
 */
final class CliBranch {

    private static final String ORIGIN = BranchRef.REMOTES + RemoteBranch.DEFAULT_REMOTE + "/";

    private static final List<String> FIELDS = List.of(
            "%(refname)",
            "%(objectname)",
            "%(committerdate:unix)",
            "%(HEAD)",
            "%(upstream:remotename)",
            "%(upstream:remoteref)",
            "%(upstream:track)",
            "%(symref)",
            "%(upstream)",
            "%(worktreepath)");
    private static final String FORMAT = FIELDS.mkString(GitOutput.REF_FIELD_SEPARATOR);
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d{1,9})");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d{1,9})");
    // What git says when it refuses to delete a branch that is not merged. It is matched in English, so git is run
    // with the C locale.
    private static final String NOT_FULLY_MERGED = "is not fully merged";
    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current)
            .reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(Branch::name);

    private static final Comparator<RemoteRef> REMOTE_ORDER = Comparator.comparing(RemoteRef::tipTime)
            .reversed()
            .thenComparing(ref -> ref.branch().shortName());

    private CliBranch() {}

    /**
     * The local and the remote-tracking branches, from a single {@code git for-each-ref}: the local branches need
     * the remote ones anyway, to tell which are pushed and which is the default.
     */
    static Either<ProcessError, AllBranches> all(Path worktree) {
        return GitProcess.read(
                worktree,
                GitCommand.reading("for-each-ref", "--format=" + FORMAT, HEADS, REMOTES),
                CliBranch::parseAll);
    }

    /**
     * Deletes the branch {@code name} where {@code scope} says: locally, and/or {@code remote}, the branch it is
     * pushed to. With both, the remote one goes first, so a failure leaves the local branch in place.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> delete(
            Path worktree, BranchName name, RemoteBranch remote, DeleteScope scope, Map<String, String> credentials) {
        return GitFailure.sequence(
                () -> scope.remote()
                        ? GitFailure.<GitError, List<ExecutedCommand>>widen(deleteRemote(worktree, remote, credentials))
                        : GitFailure.nothing(),
                () -> scope.local() ? deleteLocal(worktree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git branch -m <name> <newName>} in {@code worktree}, which refuses to overwrite a branch.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rename(
            Path worktree, BranchName name, BranchName newName) {
        return GitProcess.log(worktree, git("branch", "--move", "--", GitArgs.name(name), GitArgs.name(newName)));
    }

    /**
     * Runs {@code git branch -d <name>} in {@code worktree}.
     *
     * @return the command lines that were run, or why git refused, {@link GitError.NotFullyMerged} when forcing it
     * would delete the branch
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocal(Path worktree, BranchName name) {
        return GitProcess.log(worktree, git("branch", "--delete", "--", GitArgs.name(name)))
                .mapLeft(failure -> failure.map(CliBranch::classify));
    }

    /**
     * Runs {@code git branch -D <name>} in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocal(Path worktree, BranchName name) {
        return GitProcess.log(worktree, git("branch", "--delete", "--force", "--", GitArgs.name(name)));
    }

    /**
     * How many commits of the local branch {@code name} no other branch, remote-tracking branch or tag has: the ones
     * deleting it loses. Git matches the exclusion against the name {@code --branches} lists, without
     * {@code refs/heads/}; a branch name cannot have the characters that make it a wider glob.
     */
    static Either<ProcessError, Integer> countOnlyOn(Path worktree, BranchName name) {
        return CliLog.count(
                worktree,
                List.of(
                        GitArgs.revision(new BranchRef.Local(name)),
                        "--not",
                        "--exclude=" + GitArgs.name(name),
                        "--branches",
                        "--remotes",
                        "--tags"));
    }

    static GitError classify(ProcessError error) {
        return error instanceof GitError.CommandFailed failed && failed.says(NOT_FULLY_MERGED)
                ? new GitError.NotFullyMerged(failed.explanation().getOrElse(""))
                : error;
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemote(
            Path worktree, RemoteBranch remote, Map<String, String> credentials) {
        return CliRemote.run(
                worktree, git("push", remote.remote(), "--delete", GitArgs.name(remote.branch())), credentials);
    }

    static Either<UnexpectedOutput, AllBranches> parseAll(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(Ref::parse))
                .map(Seq::toList)
                .map(refs -> new AllBranches(branches(refs), remoteRefs(refs)));
    }

    /**
     * The remote-tracking branches among {@code refs}, not the symbolic ones like the {@code HEAD} of a remote.
     * The remote is the part of the name before the first slash, as a remote's name has none in practice.
     */
    private static List<RemoteRef> remoteRefs(List<Ref> refs) {
        return refs.filter(ref -> ref.symref().isEmpty())
                .flatMap(ref -> GitOutput.after(ref.name(), REMOTES)
                        .filter(name -> name.indexOf('/') > 0)
                        .map(name -> new RemoteRef(
                                new RemoteBranch(
                                        name.substring(0, name.indexOf('/')),
                                        new BranchName(name.substring(name.indexOf('/') + 1))),
                                ref.tip(),
                                ref.time())))
                .sorted(REMOTE_ORDER);
    }

    private static List<Branch> branches(List<Ref> refs) {
        Set<String> remote =
                refs.map(Ref::name).filter(name -> name.startsWith(REMOTES)).toSet();
        List<BranchName> local = refs.flatMap(Ref::localName);
        Option<BranchName> defaultBranch = defaultBranch(refs, local);
        return refs.flatMap(ref -> ref.localName()
                        .map(name -> new Branch(
                                name,
                                ref.head(),
                                ref.tip(),
                                ref.time(),
                                remote.contains(ref.pushedTo(name)),
                                defaultBranch.contains(name),
                                tracking(ref),
                                ref.otherWorktree())))
                .sorted(ORDER);
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that is among {@code branches}.
     */
    private static Option<BranchName> defaultBranch(List<Ref> refs, List<BranchName> branches) {
        return refs.find(ref -> ref.name().equals(ORIGIN + "HEAD"))
                .flatMap(Ref::symref)
                .flatMap(target -> GitOutput.after(target, ORIGIN))
                .map(BranchName::new)
                .orElse(() -> List.of("main", "master").map(BranchName::new).find(branches::contains));
    }

    private static Tracking tracking(Ref ref) {
        String track = ref.track().getOrElse("");
        return ref.upstream()
                .<Tracking>map(branch -> track.equals("[gone]")
                        ? new Tracking.Gone(branch)
                        : new Tracking.Tracks(new Upstream(branch, count(AHEAD, track), count(BEHIND, track))))
                .getOrElse(new Tracking.None());
    }

    private static int count(Pattern pattern, String track) {
        // At most 9 digits, so the count fits an int.
        return Option.ofOptional(pattern.matcher(track).results().findFirst())
                .map(match -> Integer.parseInt(match.group(1)))
                .getOrElse(0);
    }

    /**
     * One line of {@code for-each-ref} output.
     *
     * @param remote      the remote of the upstream, e.g. {@code origin}
     * @param remoteRef   the ref of the upstream on its remote, e.g. {@code refs/heads/main}
     * @param track       how far the branch is from its upstream, e.g. {@code [ahead 1, behind 2]}; none when
     *                    they are equal, {@code [gone]} when the upstream does not exist
     * @param symref      what a symbolic ref points to, e.g. {@code refs/remotes/origin/main}
     * @param upstreamRef the ref of the upstream: its remote-tracking ref, e.g. {@code refs/remotes/origin/main},
     *                    or the branch itself for a local one, e.g. {@code refs/heads/main}
     * @param worktree    the root of the worktree the branch is checked out in, this one included; last, as a
     *                    path is free text
     */
    private record Ref(
            String name,
            Sha tip,
            Instant time,
            boolean head,
            Option<String> remote,
            Option<String> remoteRef,
            Option<String> track,
            Option<String> symref,
            Option<String> upstreamRef,
            Option<String> worktree) {

        static Either<UnexpectedOutput, Ref> parse(String line) {
            return GitOutput.fields(line, FIELDS.size())
                    .flatMap(f -> GitOutput.sha(f.get(1))
                            .flatMap(tip -> GitOutput.epochSeconds(f.get(2))
                                    .map(time -> new Ref(
                                            f.get(0),
                                            tip,
                                            time,
                                            f.get(3).equals("*"),
                                            present(f.get(4)),
                                            present(f.get(5)),
                                            present(f.get(6)),
                                            present(f.get(7)),
                                            present(f.get(8)),
                                            present(f.get(9))))));
        }

        private static Option<String> present(String field) {
            return Option.of(field).filter(text -> !text.isEmpty());
        }

        /**
         * The name of the local branch, if this is one.
         */
        Option<BranchName> localName() {
            return GitOutput.after(name, HEADS).map(BranchName::new);
        }

        /**
         * The branch it follows, as git's configuration says, whether or not that still exists: one on a remote, or
         * one of this repository.
         */
        Option<BranchRef> upstream() {
            return remote.flatMap(r -> remoteRef
                    .flatMap(ref -> GitOutput.after(ref, HEADS))
                    .map(BranchName::new)
                    .map(branch -> r.equals(CliRemote.THIS_REPOSITORY)
                            ? new BranchRef.Local(branch)
                            : new BranchRef.Remote(new RemoteBranch(r, branch))));
        }

        /**
         * The remote-tracking ref of the branch it is pushed to, as {@link Branch#pushTarget()} says: its upstream,
         * or the one of the same name on {@code origin} without one, or when it follows a local branch.
         */
        String pushedTo(BranchName branch) {
            return upstreamRef
                    .filter(ref -> ref.startsWith(REMOTES))
                    .getOrElse(
                            () -> GitArgs.revision(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch).tracking()));
        }

        /**
         * The worktree the branch is checked out in, if that is not this one: the one where it is {@code HEAD}.
         */
        Option<Path> otherWorktree() {
            return worktree.filter(path -> !head)
                    .flatMap(path -> Try.of(() -> Path.of(path)).toOption());
        }
    }
}
