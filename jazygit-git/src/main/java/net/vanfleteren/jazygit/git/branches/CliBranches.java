package net.vanfleteren.jazygit.git.branches;

import static net.vanfleteren.jazygit.git.model.BranchRef.HEADS;
import static net.vanfleteren.jazygit.git.model.BranchRef.REMOTES;
import static net.vanfleteren.jazygit.git.process.GitCommand.git;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.history.CliHistory;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitArgs;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.git.process.GitOutput;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The {@link Branches} of a repository, through the {@code git} command line tool. The branches are read through
 * {@code git for-each-ref}, in one call for all of them. Creating a branch records its parent through
 * {@link #setParent}; deleting one on its remote too deletes it locally through {@link #deleteLocal(Path, BranchName)}.
 */
public final class CliBranches implements Branches {

    private static final String ORIGIN = BranchRef.REMOTES + GitArgs.remote(RemoteBranch.DEFAULT_REMOTE) + "/";

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
    // The ref with the time of the entry, like refs/heads/main@{1700000000} with --date=unix, then the subject: last,
    // as it is free text.
    private static final String REFLOG_FORMAT = List.of("%gD", "%gs").mkString(GitOutput.PRETTY_FIELD_SEPARATOR);
    // What starts the reflog of a branch git made, with git branch, checkout -b, switch -c or worktree add -b.
    // Git writes it untranslated.
    private static final String CREATED = "branch: Created from ";
    // What separates the ref from the time of the entry. A ref name has no "@{".
    private static final String SELECTOR = "@{";
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d{1,9})");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d{1,9})");
    // What git says when it refuses to delete a branch that is not merged. It is matched in English, so git is run
    // with the C locale.
    private static final String NOT_FULLY_MERGED = "is not fully merged";
    private static final String PARENT_PREFIX = "branch.";
    private static final String PARENT_SUFFIX = ".gh-merge-base";
    // The checked out branch first, then the most recently worked on.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current)
            .reversed()
            .thenComparing(Comparator.comparing(Branch::lastActivity).reversed())
            .thenComparing(Branch::name);

    private static final Comparator<RemoteRef> REMOTE_ORDER = Comparator.comparing(RemoteRef::tipTime)
            .reversed()
            .thenComparing(ref -> ref.branch().shortName());

    private final Path worktree;

    public CliBranches(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * The local and the remote-tracking branches, from a single {@code git for-each-ref}: the local branches need
     * the remote ones anyway, to tell which are pushed and which is the default. When the local ones were made comes
     * from their reflogs, read with one {@code git log --walk-reflogs} before.
     */
    @Override
    public Either<GitError, AllBranches> all() {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                "log",
                                "--walk-reflogs",
                                "--no-color",
                                "--no-show-signature",
                                "--date=unix",
                                "--format=" + REFLOG_FORMAT,
                                "--glob=" + HEADS,
                                "--"),
                        CliBranches::parseCreated)
                .flatMap(created -> GitProcess.read(
                        worktree,
                        GitCommand.reading("for-each-ref", "--format=" + FORMAT, HEADS, REMOTES),
                        raw -> parseAll(raw, created, BranchesInUse.elsewhere(worktree))));
    }

    /**
     * Runs {@code git branch -m <name> <newName>} in {@code worktree}, which refuses to overwrite a branch.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> rename(BranchName name, BranchName newName) {
        return GitProcess.log(worktree, git("branch", "--move", "--", GitArgs.name(name), GitArgs.name(newName)));
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> deleteLocal(BranchName name) {
        return deleteLocal(worktree, name);
    }

    /**
     * Runs {@code git branch -d <name>} in {@code worktree}.
     *
     * @return the command lines that were run, or why git refused, {@link GitError.NotFullyMerged} when forcing it
     * would delete the branch
     */
    static Either<GitFailure, List<ExecutedCommand>> deleteLocal(Path worktree, BranchName name) {
        return GitProcess.log(worktree, git("branch", "--delete", "--", GitArgs.name(name)))
                .mapLeft(failure -> failure.map(CliBranches::classify));
    }

    /**
     * Runs {@code git branch -D <name>} in {@code worktree}.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> forceDeleteLocal(BranchName name) {
        return GitProcess.log(worktree, git("branch", "--delete", "--force", "--", GitArgs.name(name)));
    }

    /**
     * How many commits of the local branch {@code name} no other branch, remote-tracking branch or tag has: the ones
     * deleting it loses. Git matches the exclusion against the name {@code --branches} lists, without
     * {@code refs/heads/}; a branch name cannot have the characters that make it a wider glob.
     */
    @Override
    public Either<GitError, Integer> countOnlyOn(BranchName name) {
        return CliHistory.count(
                worktree,
                List.of(
                        GitArgs.revision(new BranchRef.Local(name)),
                        "--not",
                        "--exclude=" + GitArgs.name(name),
                        "--branches",
                        "--remotes",
                        "--tags"));
    }

    /**
     * Runs {@code git fetch --atomic --no-write-fetch-head . refs/remotes/<remote>/<upstream>:refs/heads/<branch>…},
     * which makes the branches that are not there and fast-forwards the others, all of them or none: without a
     * {@code +}, git refuses a refspec that is no fast-forward, and it refuses a branch checked out in any worktree.
     * Then {@code git branch --set-upstream-to=<remote>/<upstream> <branch>} for each one made, and records its
     * parent.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> catchUp(List<CaughtUpBranch> branches) {
        List<CaughtUpBranch> made = branches.filter(CaughtUpBranch::made);
        return GitFailure.sequence(List.<Supplier<Either<GitFailure, List<ExecutedCommand>>>>of(() -> GitProcess.log(
                        worktree,
                        git(List.of("fetch", "--atomic", "--no-write-fetch-head", ".")
                                .appendAll(branches.map(branch -> GitArgs.refspec(
                                        branch.upstream().tracking(), new BranchRef.Local(branch.branch())))))))
                .appendAll(made.map(branch -> () -> GitProcess.log(
                        worktree,
                        git(
                                "branch",
                                "--set-upstream-to="
                                        + GitArgs.revision(branch.upstream().tracking()),
                                "--",
                                GitArgs.name(branch.branch())))))
                .appendAll(made.flatMap(branch ->
                        branch.parent().map(parent -> () -> setParent(worktree, branch.branch(), parent)))));
    }

    /**
     * The branch {@code branch} was made from, as {@code gh} records it to take as the base of its pull request.
     */
    @Override
    public Either<GitError, Option<BranchName>> parent(BranchName branch) {
        return CliConfig.readLocal(worktree, parentKey(branch)).map(value -> value.flatMap(BranchName::parse));
    }

    /**
     * Records {@code parent} as the branch {@code branch} was made from, where {@code gh} looks for it.
     */
    static Either<GitFailure, List<ExecutedCommand>> setParent(Path worktree, BranchName branch, BranchName parent) {
        return CliConfig.writeLocal(worktree, parentKey(branch), Option.of(GitArgs.name(parent)));
    }

    private static String parentKey(BranchName branch) {
        return PARENT_PREFIX + GitArgs.name(branch) + PARENT_SUFFIX;
    }

    /**
     * The parent of each branch that has one recorded, as {@code gh} does, in one {@code git config}.
     */
    @Override
    public Either<GitError, Map<BranchName, BranchName>> parents() {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(
                                "config",
                                "--local",
                                "--get-regexp",
                                "^" + PARENT_PREFIX.replace(".", "\\.") + ".*" + PARENT_SUFFIX.replace(".", "\\.")
                                        + "$"))
                .mapLeft(GitFailure::error)
                .map(answer ->
                        answer.map(output -> parseParents(output.stdout())).getOrElse(HashMap.empty()));
    }

    /**
     * The parents in {@code raw}, as {@code git config --get-regexp} lists them: the key, a space and the value per
     * line. A value that is no branch name is left out.
     */
    static Map<BranchName, BranchName> parseParents(String raw) {
        return HashMap.ofEntries(GitOutput.nonBlankLines(raw).flatMap(line -> {
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            Option<BranchName> branch = Option.when(
                            key.startsWith(PARENT_PREFIX)
                                    && key.endsWith(PARENT_SUFFIX)
                                    && key.length() > PARENT_PREFIX.length() + PARENT_SUFFIX.length(),
                            () -> key.substring(PARENT_PREFIX.length(), key.length() - PARENT_SUFFIX.length()))
                    .map(BranchName::new);
            Option<BranchName> parent =
                    Option.when(space >= 0, () -> line.substring(space + 1)).flatMap(BranchName::parse);
            return branch.flatMap(name -> parent.map(value -> Tuple.of(name, value)));
        }));
    }

    @Override
    public Map<BranchName, BaseDistance> baseDistance(
            Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        return CliBaseDistance.read(worktree, tips, parents, main);
    }

    private static GitError classify(GitError error) {
        return error instanceof GitError.CommandFailed failed && failed.says(NOT_FULLY_MERGED)
                ? new GitError.NotFullyMerged(failed.explanation().getOrElse(""))
                : error;
    }

    /**
     * The branches in {@code raw}, as {@code for-each-ref} lists them, with when the local ones were made as
     * {@code created} says.
     */
    static Either<UnexpectedOutput, AllBranches> parseAll(String raw, Map<BranchName, Creation> created) {
        return parseAll(raw, created, HashMap.empty());
    }

    /**
     * The branches in {@code raw}, as {@link #parseAll(String, Map)} reads them, with a branch that another worktree
     * rebases or bisects, as {@code inUse} has it, in that worktree: its HEAD is detached meanwhile, so
     * {@code %(worktreepath)} does not name it, but git takes it as in use there (ADR 110).
     */
    static Either<UnexpectedOutput, AllBranches> parseAll(
            String raw, Map<BranchName, Creation> created, Map<BranchName, Path> inUse) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(Ref::parse))
                .map(Seq::toList)
                .map(refs -> new AllBranches(branches(refs, created, inUse), remoteRefs(refs)));
    }

    /**
     * When git made each local branch, from their reflogs as {@code git log --walk-reflogs} lists them: the time of
     * the oldest entry of a branch, the last one listed, when that says git made it. A branch whose oldest entry is
     * another one, like that of a clone or of a pull once the older entries expired, or that has no reflog, is not
     * among them (ADR 41).
     */
    static Either<UnexpectedOutput, Map<BranchName, Creation>> parseCreated(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(ReflogEntry::parse))
                .map(entries -> entries.foldLeft(
                                HashMap.<BranchName, ReflogEntry>empty(),
                                (oldest, entry) -> oldest.put(entry.branch(), entry))
                        .filterValues(entry -> entry.subject().startsWith(CREATED))
                        .mapValues(entry ->
                                new Creation(entry.time(), entry.subject().substring(CREATED.length()))));
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

    private static List<Branch> branches(
            List<Ref> refs, Map<BranchName, Creation> created, Map<BranchName, Path> inUse) {
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
                                created.get(name)
                                        .filter(creation -> !ref.checkedOutFromItsRemote(name, creation.start()))
                                        .map(Creation::time),
                                remote.contains(ref.pushedTo(name)),
                                defaultBranch.contains(name),
                                tracking(ref),
                                ref.otherWorktree().orElse(() -> inUse.get(name).filter(path -> !ref.head())))))
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
         * one of this repository. Its remote, {@code branch.<name>.remote}, is free text git never checked: one that
         * starts with {@code -}, which git would read as an option, is no remote, so the branch follows nothing
         * (ADR 40). One with whitespace in it, like the path of a local remote, is kept.
         */
        Option<BranchRef> upstream() {
            return remote.filter(r -> !r.startsWith("-"))
                    .map(RemoteName::new)
                    .flatMap(r -> remoteRef
                            .flatMap(ref -> GitOutput.after(ref, HEADS))
                            .map(BranchName::new)
                            .map(branch -> CliRemotes.THIS_REPOSITORY.equals(r)
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
         * Whether git made the branch {@code branch} from {@code start}, the remote branch of the same name that it
         * follows: checking out someone's branch from a remote, which is getting their work rather than starting
         * one's own. Git spells that start as it was given, e.g. {@code origin/main} or
         * {@code refs/remotes/origin/main} (ADR 41).
         */
        boolean checkedOutFromItsRemote(BranchName branch, String start) {
            return upstream()
                    .exists(upstream -> upstream instanceof BranchRef.Remote(RemoteBranch remote)
                            && remote.branch().equals(branch)
                            && (start.equals(remote.shortName()) || start.equals(GitArgs.revision(remote.tracking()))));
        }

        /**
         * The worktree the branch is checked out in, if that is not this one: the one where it is {@code HEAD}.
         */
        Option<Path> otherWorktree() {
            return worktree.filter(path -> !head)
                    .flatMap(path -> Try.of(() -> Path.of(path)).toOption());
        }
    }

    /**
     * When git made a local branch, and from what.
     *
     * @param start what it was made from, as git was given it, e.g. {@code main} or {@code origin/main}
     */
    record Creation(Instant time, String start) {}

    /**
     * One entry of the reflog of a local branch, as {@code git log --walk-reflogs} lists it.
     *
     * @param time    when the branch was changed
     * @param subject what changed it, e.g. {@code branch: Created from main}
     */
    private record ReflogEntry(BranchName branch, Instant time, String subject) {

        static Either<UnexpectedOutput, ReflogEntry> parse(String line) {
            return GitOutput.fields(line, 2).flatMap(f -> {
                String selector = f.get(0);
                int at = selector.lastIndexOf(SELECTOR);
                return Option.when(at > 0 && selector.endsWith("}"), at)
                        .flatMap(i -> GitOutput.after(selector.substring(0, i), HEADS))
                        .toEither(() -> new UnexpectedOutput(line))
                        .flatMap(name -> GitOutput.epochSeconds(
                                        selector.substring(at + SELECTOR.length(), selector.length() - 1))
                                .mapLeft(e -> new UnexpectedOutput(line))
                                .map(time -> new ReflogEntry(new BranchName(name), time, f.get(1))));
            });
        }
    }
}
