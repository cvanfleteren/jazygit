package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Pattern;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.Refs.HEADS;
import static net.vanfleteren.jazygit.git.Refs.REMOTES;

/**
 * Lists, renames and deletes branches through the {@code git} command line tool. The branches are read through
 * {@code git for-each-ref}, in one call for all of them.
 */
public final class CliBranch {

    private static final String ORIGIN = Refs.REMOTES + RemoteBranch.DEFAULT_REMOTE + "/";
    private static final List<String> FIELDS = List.of("%(refname)", "%(objectname)", "%(committerdate:unix)",
            "%(HEAD)", "%(upstream:remotename)", "%(upstream:remoteref)", "%(upstream:track)", "%(symref)",
            "%(upstream)");
    private static final String FORMAT = FIELDS.mkString(GitOutput.REF_FIELD_SEPARATOR);
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d{1,9})");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d{1,9})");
    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current).reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(branch -> branch.name().value());

    private static final List<String> REMOTE_FIELDS = List.of("%(refname)", "%(objectname)", "%(committerdate:unix)",
            "%(symref)");
    private static final String REMOTE_FORMAT = REMOTE_FIELDS.mkString(GitOutput.REF_FIELD_SEPARATOR);
    private static final Comparator<RemoteRef> REMOTE_ORDER = Comparator.comparing(RemoteRef::tipTime).reversed()
            .thenComparing(ref -> ref.branch().shortName());

    private CliBranch() {
    }

    /**
     * The local branches: the checked out one first, then the most recently committed to.
     */
    public static Either<ProcessError, List<Branch>> list(Path workTree) {
        return GitProcess.read(workTree, GitCommand.reading("for-each-ref", "--format=" + FORMAT, HEADS, REMOTES),
                CliBranch::parse);
    }

    /**
     * The remote-tracking branches, the most recently committed to first; not the {@code HEAD} of a remote,
     * which only names one of the others.
     */
    public static Either<ProcessError, List<RemoteRef>> remotes(Path workTree) {
        return GitProcess.read(workTree, GitCommand.reading("for-each-ref", "--format=" + REMOTE_FORMAT, REMOTES),
                CliBranch::parseRemotes);
    }

    /**
     * Deletes the branch {@code name} where {@code scope} says: locally, and/or {@code remote}, the branch it is
     * pushed to. With both, the remote one goes first, so a failure leaves the local branch in place.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> delete(Path workTree, Branch.Name name,
                                                                          RemoteBranch remote, DeleteScope scope,
                                                                          Map<String, String> credentials) {
        return GitFailure.sequence(
                () -> scope.remote() ? deleteRemote(workTree, remote, credentials) : GitFailure.nothing(),
                () -> scope.local() ? deleteLocal(workTree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git branch -m <name> <newName>} in {@code workTree}, which refuses to overwrite a branch.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rename(Path workTree, Branch.Name name,
                                                                          Branch.Name newName) {
        return GitProcess.log(workTree, git("branch", "--move", name.value(), newName.value()));
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteLocal(Path workTree, Branch.Name name) {
        return GitProcess.log(workTree, git("branch", "--delete", "--force", name.value()));
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemote(Path workTree, RemoteBranch remote,
                                                                                Map<String, String> credentials) {
        return CliRemote.run(workTree, git("push", remote.remote(), "--delete", remote.branch().value()), credentials);
    }

    static Either<UnexpectedOutput, List<RemoteRef>> parseRemotes(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(CliBranch::remoteRef))
                .map(refs -> refs.flatMap(ref -> ref).toList().sorted(REMOTE_ORDER));
    }

    /**
     * A line of {@code for-each-ref} output for a remote-tracking ref; none for a symbolic one.
     * The remote is the part of the name before the first slash, as a remote's name has none in practice.
     */
    private static Either<UnexpectedOutput, Option<RemoteRef>> remoteRef(String line) {
        return GitOutput.fields(line, REMOTE_FIELDS.size()).flatMap(f -> GitOutput.sha(f.get(1)).flatMap(tip ->
                GitOutput.epochSeconds(f.get(2)).map(time -> GitOutput.after(f.get(0), REMOTES)
                        .filter(name -> f.get(3).isEmpty() && name.indexOf('/') > 0)
                        .map(name -> new RemoteRef(new RemoteBranch(name.substring(0, name.indexOf('/')),
                                new Branch.Name(name.substring(name.indexOf('/') + 1))), tip, time)))));
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
     */
    private record Ref(String name, Sha tip, Instant time, boolean head, Option<String> remote,
                       Option<String> remoteRef, Option<String> track, Option<String> symref,
                       Option<String> upstreamRef) {

        static Either<UnexpectedOutput, Ref> parse(String line) {
            return GitOutput.fields(line, FIELDS.size()).flatMap(f -> GitOutput.sha(f.get(1)).flatMap(tip ->
                    GitOutput.epochSeconds(f.get(2)).map(time -> new Ref(f.get(0), tip, time, f.get(3).equals("*"),
                            present(f.get(4)), present(f.get(5)), present(f.get(6)), present(f.get(7)),
                            present(f.get(8))))));
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
            return remote.flatMap(r -> remoteRef.flatMap(ref -> GitOutput.after(ref, HEADS))
                    .map(branch -> new RemoteBranch(r, new Branch.Name(branch))));
        }

        /**
         * The remote-tracking ref of the branch it is pushed to: its upstream, or the one of the same name
         * on {@code origin}.
         */
        String pushedTo(Branch.Name branch) {
            return upstreamRef.getOrElse(() -> ORIGIN + branch.value());
        }
    }

    static Either<UnexpectedOutput, List<Branch>> parse(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(Ref::parse)).map(refs -> branches(refs.toList()));
    }

    private static List<Branch> branches(List<Ref> refs) {
        Set<String> remote = refs.map(Ref::name).filter(name -> name.startsWith(REMOTES)).toSet();
        List<Branch.Name> local = refs.flatMap(Ref::localName);
        Option<Branch.Name> defaultBranch = defaultBranch(refs, local);
        return refs
                .flatMap(ref -> ref.localName().map(name -> new Branch(name, ref.head(), ref.tip(), ref.time(),
                        remote.contains(ref.pushedTo(name)),
                        defaultBranch.contains(name),
                        tracking(ref))))
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
}
