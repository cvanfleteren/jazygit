package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.Refs.HEADS;
import static net.vanfleteren.jazygit.git.Refs.REMOTES;

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

    private static final String ORIGIN = Refs.REMOTES + RemoteBranch.DEFAULT_REMOTE + "/";
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
    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current)
            .reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(branch -> branch.name().value());

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
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> delete(
            Path worktree, Branch.Name name, RemoteBranch remote, DeleteScope scope, Map<String, String> credentials) {
        return GitFailure.sequence(
                () -> scope.remote() ? deleteRemote(worktree, remote, credentials) : GitFailure.nothing(),
                () -> scope.local() ? deleteLocal(worktree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git branch -m <name> <newName>} in {@code worktree}, which refuses to overwrite a branch.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rename(
            Path worktree, Branch.Name name, Branch.Name newName) {
        return GitProcess.log(worktree, git("branch", "--move", name.value(), newName.value()));
    }

    /**
     * Runs {@code git branch -D <name>} in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteLocal(Path worktree, Branch.Name name) {
        return GitProcess.log(worktree, git("branch", "--delete", "--force", name.value()));
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemote(
            Path worktree, RemoteBranch remote, Map<String, String> credentials) {
        return CliRemote.run(
                worktree,
                git("push", remote.remote(), "--delete", remote.branch().value()),
                credentials);
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
                                        new Branch.Name(name.substring(name.indexOf('/') + 1))),
                                ref.tip(),
                                ref.time())))
                .sorted(REMOTE_ORDER);
    }

    private static List<Branch> branches(List<Ref> refs) {
        Set<String> remote =
                refs.map(Ref::name).filter(name -> name.startsWith(REMOTES)).toSet();
        List<Branch.Name> local = refs.flatMap(Ref::localName);
        Option<Branch.Name> defaultBranch = defaultBranch(refs, local);
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
    private static Option<Branch.Name> defaultBranch(List<Ref> refs, List<Branch.Name> branches) {
        return refs.find(ref -> ref.name().equals(ORIGIN + "HEAD"))
                .flatMap(Ref::symref)
                .flatMap(target -> GitOutput.after(target, ORIGIN))
                .map(Branch.Name::new)
                .orElse(() -> List.of("main", "master").map(Branch.Name::new).find(branches::contains));
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
     * @param upstreamRef the remote-tracking ref of the upstream, e.g. {@code refs/remotes/origin/main}
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
        Option<Branch.Name> localName() {
            return GitOutput.after(name, HEADS).map(Branch.Name::new);
        }

        /**
         * The branch it follows on its remote, as git's configuration says, whether or not that still exists.
         */
        Option<RemoteBranch> upstream() {
            return remote.flatMap(r -> remoteRef
                    .flatMap(ref -> GitOutput.after(ref, HEADS))
                    .map(branch -> new RemoteBranch(r, new Branch.Name(branch))));
        }

        /**
         * The remote-tracking ref of the branch it is pushed to: its upstream, or the one of the same name
         * on {@code origin}.
         */
        String pushedTo(Branch.Name branch) {
            return upstreamRef.getOrElse(() -> ORIGIN + branch.value());
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
