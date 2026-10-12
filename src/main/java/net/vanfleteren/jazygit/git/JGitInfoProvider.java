package net.vanfleteren.jazygit.git;

import io.vavr.CheckedFunction0;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.NoWorkTree;
import net.vanfleteren.jazygit.git.GitError.NotARepository;
import net.vanfleteren.jazygit.git.GitError.OpenError;
import net.vanfleteren.jazygit.git.GitError.OpenFailed;
import net.vanfleteren.jazygit.git.GitError.RepositoryUnreadable;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitError.UnknownBranch;
import net.vanfleteren.jazygit.git.model.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;

/**
 * {@link GitInfoProvider} backed by a real repository. The working tree status comes from the
 * {@code git status} command line tool, branches and commits are read through JGit,
 * and checkouts go through the {@code git checkout} command line tool.
 */
public final class JGitInfoProvider implements GitInfoProvider, AutoCloseable {

    private static final int MAX_COMMITS = 300;
    private static final String DEFAULT_REMOTE = "origin";
    private static final String ORIGIN = "refs/remotes/origin/";

    private final Repository repository;
    private final Git git;
    private final Path workTree;
    // The ahead/behind counts per branch, so the revwalk only reruns when a tip moves. Replaced on
    // every read, which drops deleted branches.
    private volatile Map<String, Tracking> trackingCache = HashMap.empty();

    /**
     * The ahead/behind counts of a branch, valid for these tips of the branch and its upstream.
     */
    private record Tracking(String trackingRef, String localOid, String remoteOid, int ahead, int behind) {
    }

    private JGitInfoProvider(Repository repository, Path workTree) {
        this.repository = repository;
        this.git = new Git(repository);
        this.workTree = workTree;
    }

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does. It fails when {@code startDir} is not inside a git repository, the
     * repository has no working tree, or git cannot be run.
     */
    public static Either<OpenError, JGitInfoProvider> open(Path startDir) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(startDir.toFile());
        return Option.of(builder.getGitDir())
                .<OpenError>toEither(() -> new NotARepository(startDir))
                .flatMap(gitDir -> Try.of(() -> builder.setMustExist(true).build())
                        .toEither()
                        .<OpenError>mapLeft(e -> new OpenFailed(gitDir.toPath(), String.valueOf(e.getMessage()))))
                .flatMap(JGitInfoProvider::withWorkTree)
                // Fail at startup rather than on first use when git is not available.
                .flatMap(provider -> Either.<OpenError, JGitInfoProvider>narrow(CliStatus.read(provider.workTree)
                                .map(status -> provider))
                        .peekLeft(error -> provider.close()));
    }

    private static Either<OpenError, JGitInfoProvider> withWorkTree(Repository repository) {
        return repository.isBare()
                ? Either.<OpenError, JGitInfoProvider>left(new NoWorkTree(repository.getDirectory().toPath()))
                .peekLeft(error -> repository.close())
                : Either.right(new JGitInfoProvider(repository, repository.getWorkTree().toPath()));
    }

    @Override
    public String repositoryName() {
        return workTree.getFileName().toString();
    }

    @Override
    public Either<ProcessError, RepoStatus> status() {
        return CliStatus.read(workTree);
    }

    @Override
    public Either<RepositoryUnreadable, List<Branch>> branches() {
        return readBranches();
    }

    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> BRANCH_ORDER = Comparator.comparing(Branch::current).reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(Branch::name);

    /**
     * A branch as read, with the ahead/behind counts it was given, if it has an upstream.
     */
    private record BranchRead(Branch branch, Option<Tracking> tracking) {
    }

    private Either<RepositoryUnreadable, List<Branch>> readBranches() {
        return read(() -> {
            List<Ref> refs = List.ofAll(git.branchList().call());
            String fullBranch = repository.getFullBranch();
            Option<String> defaultBranch = defaultBranch(refs.map(ref -> Repository.shortenRefName(ref.getName())));
            try (RevWalk walk = new RevWalk(repository)) {
                return Try.traverse(refs, ref -> Try.of(() -> branchOf(walk, ref, fullBranch, defaultBranch)))
                        .get()
                        .toList();
            }
        }).map(read -> {
            trackingCache = read.flatMap(b -> b.tracking().map(t -> Tuple.of(b.branch().name(), t)))
                    .toMap(Function.identity());
            return read.map(BranchRead::branch).sorted(BRANCH_ORDER);
        });
    }

    private BranchRead branchOf(RevWalk walk, Ref ref, String fullBranch, Option<String> defaultBranch)
            throws IOException {
        String name = Repository.shortenRefName(ref.getName());
        String tip = ref.getObjectId().name();
        Option<Tracking> tracking = tracking(name, tip);
        return new BranchRead(new Branch(name, ref.getName().equals(fullBranch), new Sha(tip),
                Instant.ofEpochSecond(walk.parseCommit(ref.getObjectId()).getCommitTime()),
                hasRemote(name),
                defaultBranch.contains(name),
                tracking.map(Tracking::ahead).getOrElse(0),
                tracking.map(Tracking::behind).getOrElse(0)), tracking);
    }

    @Override
    public Either<ProcessError, List<Stash>> stashes() {
        return CliStash.list(workTree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> drop(String ref) {
        return CliStash.drop(workTree, ref);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> pop(String ref) {
        return CliStash.pop(workTree, ref);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> stash(String message) {
        return CliStash.push(workTree, message);
    }

    @Override
    public Either<RepositoryUnreadable, List<Commit>> commits() {
        return head().flatMap(head -> head.fold(() -> Either.right(List.empty()), this::log));
    }

    @Override
    public Either<LogError, List<Commit>> log(String branch) {
        return Either.<LogError, Option<ObjectId>>narrow(read(() -> Option.of(repository.resolve(Constants.R_HEADS + branch))))
                .flatMap(tip -> tip.<LogError>toEither(() -> new UnknownBranch(branch)))
                .flatMap(tip -> Either.narrow(log(tip)));
    }

    private Either<RepositoryUnreadable, List<Commit>> log(ObjectId start) {
        return read(() -> CommitGraph.arrange(List.ofAll(git.log().add(start).setMaxCount(MAX_COMMITS).call())
                .map(JGitInfoProvider::toCommit)));
    }

    private Either<RepositoryUnreadable, Option<ObjectId>> head() {
        return read(() -> Option.of(repository.resolve(Constants.HEAD)));
    }

    /**
     * Runs a read through JGit.
     */
    private static <T> Either<RepositoryUnreadable, T> read(CheckedFunction0<T> read) {
        return Try.of(read).toEither().mapLeft(e -> new RepositoryUnreadable(String.valueOf(e.getMessage())));
    }

    private static Commit toCommit(RevCommit c) {
        PersonIdent author = c.getAuthorIdent();
        return new Commit(new Sha(c.name()), author.getName(), author.getEmailAddress(),
                author.getWhenAsInstant(), c.getShortMessage(), extendedMessage(c.getFullMessage()),
                List.of(c.getParents()).map(parent -> new Sha(parent.name())));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        String[] parts = fullMessage.split("\\R\\s*\\R", 2);
        return parts.length < 2 ? "" : parts[1].strip();
    }

    @Override
    public Either<GitFailure<CheckoutError>, List<String>> checkout(String branch) {
        return CliCheckout.checkout(workTree, branch);
    }

    @Override
    public Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(String branch) {
        return CliCheckout.checkoutWithStash(workTree, branch);
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that is among {@code branches}.
     */
    private Option<String> defaultBranch(List<String> branches) throws IOException {
        return Option.of(repository.exactRef(ORIGIN + "HEAD"))
                .filter(Ref::isSymbolic)
                .map(head -> head.getTarget().getName())
                .filter(target -> target.startsWith(ORIGIN))
                .map(target -> target.substring(ORIGIN.length()))
                .orElse(() -> List.of("main", "master").find(branches::contains));
    }

    /**
     * How far {@code branch} is ahead of and behind its upstream, when it has one that exists. The
     * counts of the previous read are reused while neither tip has moved.
     */
    private Option<Tracking> tracking(String branch, String localOid) throws IOException {
        String trackingRef = new BranchConfig(repository.getConfig(), branch).getRemoteTrackingBranch();
        Ref remote = trackingRef == null ? null : repository.exactRef(trackingRef);
        if (remote == null || remote.getObjectId() == null) {
            return Option.none();
        }
        String remoteOid = remote.getObjectId().name();
        Option<Tracking> cached = trackingCache.get(branch)
                .filter(t -> t.trackingRef().equals(trackingRef) && t.localOid().equals(localOid)
                        && t.remoteOid().equals(remoteOid));
        if (cached.isDefined()) {
            return cached;
        }
        return Option.of(BranchTrackingStatus.of(repository, branch))
                .map(status -> new Tracking(trackingRef, localOid, remoteOid,
                        status.getAheadCount(), status.getBehindCount()));
    }

    /**
     * Whether the remote-tracking ref of {@code branch} exists. The remote is the one
     * {@link #remoteOf}.
     */
    private boolean hasRemote(String branch) throws IOException {
        return repository.exactRef("refs/remotes/" + remoteOf(branch) + "/" + branch) != null;
    }

    /**
     * The remote {@code branch} tracks, or {@code origin}.
     */
    private String remoteOf(String branch) {
        return Option.of(repository.getConfig().getString("branch", branch, "remote")).getOrElse(DEFAULT_REMOTE);
    }

    private boolean hasUpstream(String branch) {
        return repository.getConfig().getString("branch", branch, "merge") != null;
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> createBranch(String name, String startPoint) {
        return CliCheckout.createBranch(workTree, name, startPoint);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> push(String branch, boolean forceWithLease) {
        return CliBranch.push(workTree, remoteOf(branch), branch, hasUpstream(branch), forceWithLease);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> rebase(String onto) {
        return CliRebase.onto(workTree, onto);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, boolean local, boolean remote) {
        return GitFailure.sequence(
                () -> remote ? CliBranch.deleteRemote(workTree, remoteOf(name), name) : Either.right(List.empty()),
                () -> local ? CliBranch.deleteLocal(workTree, name) : Either.right(List.empty()));
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> stage(List<String> paths) {
        return CliIndex.add(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> unstageNew(List<String> paths) {
        return CliIndex.removeCached(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> unstage(List<String> paths) {
        return CliIndex.reset(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> commit(CommitMessage message) {
        return CliIndex.commit(workTree, message);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> amendLastCommit() {
        return CliIndex.amend(workTree);
    }

    @Override
    public Either<GitFailure<RewordError>, List<String>> reword(Sha sha, CommitMessage message) {
        return head()
                .mapLeft(error -> new GitFailure<RewordError>(error, List.empty()))
                .flatMap(head -> head.exists(id -> id.name().equals(sha.value()))
                        ? GitFailure.widen(CliIndex.reword(workTree, message))
                        : CliRebase.reword(workTree, sha, message));
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> squash(Sha sha) {
        return CliRebase.squash(workTree, sha);
    }

    @Override
    public Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan) {
        return GitFailure.<DiscardError>sequence(
                () -> GitFailure.widen(CliIndex.removeAdded(workTree, plan.added())),
                () -> GitFailure.widen(CliIndex.checkoutFromHead(workTree, plan.fromHead())),
                () -> GitFailure.widen(CliIndex.checkoutFromIndex(workTree, plan.fromIndex())),
                () -> CliIndex.deleteUntracked(workTree, plan.untracked()));
    }

    @Override
    public Either<ProcessError, String> commitChanges(Sha sha) {
        return CliShow.changes(workTree, sha);
    }

    @Override
    public Either<DiffError, Diffs> diff(List<FileEntry> files) {
        return CliDiff.diff(workTree, files);
    }

    @Override
    public void close() {
        git.close();
        repository.close();
    }
}
