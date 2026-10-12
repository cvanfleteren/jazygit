package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

/**
 * {@link GitInfoProvider} backed by a real repository. The working tree status comes from the
 * {@code git status} command line tool, branches and commits are read through JGit,
 * and checkouts go through the {@code git checkout} command line tool.
 */
public final class JGitInfoProvider implements GitInfoProvider, AutoCloseable {

    private static final int MAX_COMMITS = 300;
    private static final String DEFAULT_REMOTE = "origin";

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

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does.
     *
     * @throws IllegalStateException if {@code startDir} is not inside a git repository, the
     *                               repository has no working tree, or git cannot be run
     */
    public JGitInfoProvider(Path startDir) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(startDir.toFile());
        if (builder.getGitDir() == null) {
            throw new IllegalStateException(Messages.get("git.notRepository", startDir));
        }
        try {
            this.repository = builder.setMustExist(true).build();
        } catch (IOException e) {
            throw new UncheckedIOException(Messages.get("git.openFailed", builder.getGitDir()), e);
        }
        this.git = new Git(repository);
        try {
            this.workTree = repository.getWorkTree().toPath();
            // Fail at startup rather than on first use when git is not available.
            readOrThrow(CliStatus.read(workTree));
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public String repositoryName() {
        return workTree.getFileName().toString();
    }

    @Override
    public RepoStatus status() {
        return readOrThrow(CliStatus.read(workTree));
    }

    @Override
    public List<Branch> branches() {
        try {
            String fullBranch = repository.getFullBranch();
            Map<String, Integer> modified = commitTimes();
            Option<String> defaultBranch = defaultBranch();
            List<Branch> branches = List.empty();
            Map<String, Tracking> tracking = HashMap.empty();
            for (Ref ref : git.branchList().call()) {
                String name = Repository.shortenRefName(ref.getName());
                Option<Tracking> counts = tracking(name, ref.getObjectId().name());
                if (counts.isDefined()) {
                    tracking = tracking.put(name, counts.get());
                }
                branches = branches.append(new Branch(name,
                        ref.getName().equals(fullBranch), ref.getObjectId().name(),
                        Instant.ofEpochSecond(modified.apply(ref.getObjectId().name())),
                        hasRemote(name),
                        defaultBranch.equals(Option.of(name)),
                        counts.map(Tracking::ahead).getOrElse(0),
                        counts.map(Tracking::behind).getOrElse(0)));
            }
            trackingCache = tracking;
            // The checked out branch first, then the most recently committed to.
            return branches
                    .sorted(Comparator.comparing(Branch::current).reversed()
                            .thenComparing(Comparator.comparingInt((Branch b) -> modified.apply(b.tipOid())).reversed())
                            .thenComparing(Branch::name));
        } catch (IOException e) {
            throw new UncheckedIOException(Messages.get("git.readBranchesFailed"), e);
        } catch (GitAPIException e) {
            throw new IllegalStateException(Messages.get("git.readBranchesFailed"), e);
        }
    }

    @Override
    public List<Stash> stashes() {
        return readOrThrow(CliStash.list(workTree));
    }

    @Override
    public List<String> drop(String ref) {
        return orThrow(CliStash.drop(workTree, ref));
    }

    @Override
    public List<String> pop(String ref) {
        return orThrow(CliStash.pop(workTree, ref));
    }

    @Override
    public List<String> stash(String message) {
        return orThrow(CliStash.push(workTree, message));
    }

    @Override
    public List<Commit> commits() {
        try {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? List.of() : CommitGraph.arrange(log(head));
        } catch (IOException e) {
            throw new UncheckedIOException(Messages.get("git.readLogFailed"), e);
        }
    }

    @Override
    public List<Commit> log(String branch) {
        try {
            ObjectId tip = repository.resolve(Constants.R_HEADS + branch);
            if (tip == null) {
                throw new IllegalStateException(Messages.get("git.unknownBranch", branch));
            }
            return CommitGraph.arrange(log(tip));
        } catch (IOException e) {
            throw new UncheckedIOException(Messages.get("git.readBranchLogFailed", branch), e);
        }
    }

    private List<Commit> log(ObjectId start) throws IOException {
        try {
            return List.ofAll(git.log().add(start).setMaxCount(MAX_COMMITS).call())
                    .map(JGitInfoProvider::toCommit);
        } catch (GitAPIException e) {
            throw new IllegalStateException(Messages.get("git.readLogFailed"), e);
        }
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
    public List<String> checkout(String branch) {
        return orThrow(CliCheckout.checkout(workTree, branch));
    }

    @Override
    public List<String> checkoutWithStash(String branch) {
        return orThrow(CliCheckout.checkoutWithStash(workTree, branch));
    }

    /**
     * The commit time of the tip of every local branch, by object id.
     */
    private Map<String, Integer> commitTimes() throws IOException, GitAPIException {
        Map<String, Integer> modified = HashMap.empty();
        try (RevWalk walk = new RevWalk(repository)) {
            for (Ref ref : git.branchList().call()) {
                modified = modified.put(ref.getObjectId().name(), walk.parseCommit(ref.getObjectId()).getCommitTime());
            }
        }
        return modified;
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that exists.
     */
    private Option<String> defaultBranch() throws IOException {
        String prefix = "refs/remotes/origin/";
        Ref head = repository.exactRef(prefix + "HEAD");
        if (head != null && head.isSymbolic() && head.getTarget().getName().startsWith(prefix)) {
            return Option.of(head.getTarget().getName().substring(prefix.length()));
        }
        return List.of("main", "master")
                .find(name -> {
                    try {
                        return repository.exactRef("refs/heads/" + name) != null;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
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
    public List<String> createBranch(String name, String startPoint) {
        return orThrow(CliCheckout.createBranch(workTree, name, startPoint));
    }

    @Override
    public List<String> push(String branch, boolean forceWithLease) {
        return orThrow(CliBranch.push(workTree, remoteOf(branch), branch, hasUpstream(branch), forceWithLease));
    }

    @Override
    public List<String> rebase(String onto) {
        return orThrow(CliRebase.onto(workTree, onto));
    }

    @Override
    public List<String> deleteBranch(String name, boolean local, boolean remote) {
        return orThrow(GitFailure.sequence(
                () -> remote ? CliBranch.deleteRemote(workTree, remoteOf(name), name) : Either.right(List.empty()),
                () -> local ? CliBranch.deleteLocal(workTree, name) : Either.right(List.empty())));
    }

    @Override
    public List<String> stage(List<String> paths) {
        return orThrow(CliIndex.add(workTree, paths));
    }

    @Override
    public List<String> unstageNew(List<String> paths) {
        return orThrow(CliIndex.removeCached(workTree, paths));
    }

    @Override
    public List<String> unstage(List<String> paths) {
        return orThrow(CliIndex.reset(workTree, paths));
    }

    @Override
    public List<String> commit(String summary, String description) {
        return orThrow(CliIndex.commit(workTree, new CommitMessage(summary, description)));
    }

    @Override
    public List<String> amendLastCommit() {
        return orThrow(CliIndex.amend(workTree));
    }

    private String headOid() {
        try {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? "" : head.name();
        } catch (IOException e) {
            throw new UncheckedIOException(Messages.get("git.readLogFailed"), e);
        }
    }

    @Override
    public List<String> reword(Sha sha, String summary, String description) {
        CommitMessage message = new CommitMessage(summary, description);
        return sha.value().equals(headOid())
                ? orThrow(CliIndex.reword(workTree, message))
                : orThrow(CliRebase.reword(workTree, sha, message));
    }

    @Override
    public List<String> squash(Sha sha) {
        return orThrow(CliRebase.squash(workTree, sha));
    }

    @Override
    public List<String> discard(DiscardPlan plan) {
        return orThrow(GitFailure.<DiscardError>sequence(
                () -> GitFailure.widen(CliIndex.removeAdded(workTree, plan.added())),
                () -> GitFailure.widen(CliIndex.checkoutFromHead(workTree, plan.fromHead())),
                () -> GitFailure.widen(CliIndex.checkoutFromIndex(workTree, plan.fromIndex())),
                () -> CliIndex.deleteUntracked(workTree, plan.untracked())));
    }

    @Override
    public String commitChanges(Sha sha) {
        return readOrThrow(CliShow.changes(workTree, sha));
    }

    @Override
    public Diffs diff(List<FileEntry> files) {
        return readOrThrow(CliDiff.diff(workTree, files));
    }

    /**
     * Bridges to callers that still expect an exception.
     */
    private static List<String> orThrow(Either<? extends GitFailure<?>, List<String>> outcome) {
        return outcome.getOrElseThrow(GitFailure::toException);
    }

    private static <T> T readOrThrow(Either<? extends GitError, T> outcome) {
        return outcome.getOrElseThrow(error -> new IllegalStateException(GitErrorText.of(error)));
    }

    @Override
    public void close() {
        git.close();
        repository.close();
    }
}
