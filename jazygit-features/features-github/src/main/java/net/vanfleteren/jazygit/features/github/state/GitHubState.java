package net.vanfleteren.jazygit.features.github.state;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.MergeStatus;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.core.gh.timing.Eta;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.StatedBases;

/**
 * What GitHub reports about the branches of the repository, which the branches pane shows.
 *
 * @param pullRequests        the open pull requests on GitHub, by the remote branch they merge from; none until they
 *                            were read once, and the ones read last when reading them again failed
 * @param pullRequestsFailure why the pull requests could not be read the last time, if they could not
 * @param protectedBranches   the branches that are protected on GitHub, roots that others are built on; none until
 *                            they were read, once while jazygit runs, as they seldom change (ADR 72)
 * @param branchChecks        how the CI checks are doing on the branches on GitHub; none until they were read once,
 *                            and the ones read last when reading them again failed
 * @param again               the running reads to start again once they are done, as they may have missed a change
 *                            just made; see {@link GitHubReads}
 * @param merges              the merges GitHub was asked for, by the number of their pull request, kept apart from
 *                            what is read so a read does not wipe how they went; only in memory (ADR 24)
 * @param checkTimes          how long the checks of the repository took, as far as it was learned; none until a read
 *                            told which repository it is (ADR 32)
 * @param pendingChecks       the checks one by one, with their timing, of the branches on GitHub whose checks are
 *                            pending; none until they were read once (ADR 32)
 * @param nextRead            the id the next read of the details of a pull request, or of the open pull requests, gets,
 *                            higher than every one given out before (ADR 34, 119)
 * @param listRead            the id of the read of the open pull requests that runs, or ran last: one at a time runs
 *                            (ADR 119)
 * @param readsFrom           by the number of a pull request, the lowest id of a read of its details that is still
 *                            taken: past the last one taken, and past every one given out before a change to it, so a
 *                            read that started earlier does not show the change undone (ADR 34)
 * @param failing             the reads that keep failing, which the timers start again only once they waited longer the
 *                            more they failed: a repository without a remote on GitHub, gh logged out or offline
 *                            (ADR 58)
 * @param stackChange         a change made to the stacks that the pull requests read may not show yet, which they are
 *                            read again for until they do (ADR 73)
 * @param repository          the repository on GitHub, as {@code owner/name}, once its pull requests were read
 * @param heads               the remotes each open pull request's branch is in, by its number, as far as the
 *                            repositories of the remotes tell (ADR 177): a pull request is a branch's only when its
 *                            branch is in the repository of the remote that branch is pushed to, whichever of that
 *                            repository's remotes it is; one not among them matches by name
 * @param forks               the open pull requests whose branch is in another repository, a fork
 */
@With
public record GitHubState(
        Option<Map<BranchName, PullRequest>> pullRequests,
        Option<Failure> pullRequestsFailure,
        Option<Set<BranchName>> protectedBranches,
        Option<GitHubChecks> branchChecks,
        Set<Cmd> again,
        Map<PrNumber, PullRequestMerge> merges,
        Option<CheckTimes> checkTimes,
        Option<PendingChecks> pendingChecks,
        long nextRead,
        long listRead,
        Map<PrNumber, Long> readsFrom,
        Map<Cmd, FailingRead> failing,
        Option<StackChange> stackChange,
        Option<RepoSlug> repository,
        Map<PrNumber, Set<RemoteName>> heads,
        Set<PrNumber> forks) {

    public static final GitHubState NOT_READ = new GitHubState(
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            HashSet.empty(),
            HashMap.empty(),
            Option.none(),
            Option.none(),
            0,
            0,
            HashMap.empty(),
            HashMap.empty(),
            Option.none(),
            Option.none(),
            HashMap.empty(),
            HashSet.empty());

    /**
     * Where the model keeps what GitHub reports, as a part (ADR 157) rather than a field the model names: not kept until
     * something was read, so a model that never read from GitHub keeps nothing of it.
     */
    public static final Part<GitHubState> PART = Part.of("github.state", NOT_READ)
            .stating(GitHubState::bases)
            .naming(GitHubState::repository)
            // F on a branch with a pull request lists the files of that (ADR 61, 171).
            .listingFiles((state, branch) ->
                    state.pullRequest(branch).map(pullRequest -> new PullRequestMsg.FilesRequested(branch.name())))
            // Another worktree of the repository has the same branches on GitHub (ADR 182).
            .carrying(state -> Option.of(state.carried()));

    /**
     * What another worktree of the same repository opens with (ADR 182): what GitHub said of its branches and of the
     * merges it was asked for, but not the reads to start again, nor that GitHub is being asked how a merge is doing:
     * those answers go to the worktree shown before, so a pending merge is asked about again from here.
     */
    public GitHubState carried() {
        return withAgain(HashSet.empty()).withMerges(merges.mapValues(merge -> merge.checked(merge.status())));
    }

    /**
     * How the latest merge that merges pull request {@code number} went, or is going: the merge of it, or of one
     * above it in its stack. Only one merge merges it, as a later one replaces it ({@link #withMerge}).
     */
    public Option<MergeStatus> merge(PrNumber number) {
        return merges.values().find(merge -> merge.merges(number)).map(PullRequestMerge::status);
    }

    /**
     * How the latest merge of {@code pullRequest} went, or is going, if there is one.
     */
    public Option<MergeStatus> merge(Option<PullRequest> pullRequest) {
        return pullRequest.flatMap(pr -> merge(pr.number()));
    }

    /**
     * Whether GitHub is merging pull request {@code number} still, so it is not to be merged again meanwhile.
     */
    public boolean merging(PrNumber number) {
        return merge(number).exists(status -> status instanceof MergeStatus.Pending);
    }

    /**
     * The numbers of the open pull requests below pull request {@code number} in its stack, which merging it merges
     * too; none when it is not listed, or not stacked.
     */
    public List<PrNumber> below(PrNumber number) {
        List<PullRequest> open = open();
        return open.find(pr -> pr.number().equals(number))
                .flatMap(PullRequest::stack)
                .map(stack -> open.filter(other -> other.stack()
                                .exists(its ->
                                        its.number().equals(stack.number()) && its.position() < stack.position()))
                        .map(PullRequest::number)
                        .sorted())
                .getOrElse(List.empty());
    }

    /**
     * This state with {@code merge} as the merge of its pull request, and of the ones below it: it replaces how earlier
     * merges went for those, each of which still tells how it went for its own pull request, when that is not one of
     * them. Once it failed, it keeps how they stood then, as listed, and that only a read given out from then on tells
     * whether they changed (ADR 119).
     */
    public GitHubState withMerge(PullRequestMerge merge) {
        List<PrNumber> merged = merge.below().prepend(merge.number());
        return withMerges(merges.removeAll(merged)
                .mapValues(earlier -> earlier.withoutBelow(merged))
                .put(merge.number(), merge.failedAmong(open(), nextRead)));
    }

    /**
     * The state once the read with id {@link #nextRead} was given out.
     */
    public GitHubState withReadGivenOut() {
        return withNextRead(nextRead + 1);
    }

    /**
     * The state once {@code cmds} started: a read of the open pull requests among them, a {@link ListRead}, gets the id
     * {@link #nextRead} (ADR 119).
     */
    public GitHubState withStarted(List<Cmd> cmds) {
        return cmds.exists(ListRead.class::isInstance) ? withListRead(nextRead).withReadGivenOut() : this;
    }

    /**
     * Whether read {@code read} of the details of pull request {@code number} is still taken when it comes back: no
     * later one was taken, and it started after the last change to it.
     */
    public boolean current(PrNumber number, long read) {
        return read >= readsFrom.getOrElse(number, 0L);
    }

    /**
     * The state once read {@code read} of the details of pull request {@code number} was taken: one that started
     * before it is no longer.
     */
    public GitHubState withReadTaken(PrNumber number, long read) {
        return withReadsFrom(readsFrom.put(number, read + 1));
    }

    /**
     * The state once pull request {@code number} was changed: no read of it given out before is taken anymore.
     */
    public GitHubState withChanged(PrNumber number) {
        return withReadsFrom(readsFrom.put(number, nextRead));
    }

    /**
     * This state with the open pull requests {@code listed}, by the branch they merge from. Several can merge from the
     * same branch, into different bases: the newest, the one with the highest number, is kept, so the choice does not
     * depend on the order gh lists them in. They are what read {@link #listRead} listed.
     */
    public GitHubState withListed(List<PullRequest> listed) {
        // Vavr's toMap has no merge function; a later entry replaces an earlier one, so ascending numbers keep the
        // newest.
        return withOpen(
                Option.of(listed.sortBy(PullRequest::number).toMap(pr -> FromGitHub.branch(pr.headBranch()), pr -> pr)),
                listRead);
    }

    /**
     * This state with the pull request {@code details} are about as read {@code read} just read them: as it now is,
     * or no longer listed once it is merged or closed. Unchanged when it is not listed.
     */
    public GitHubState withRead(PullRequestDetails details, long read) {
        return withOpen(
                pullRequests.map(open -> open.find(listed -> listed._2.number().equals(details.number()))
                        .map(listed ->
                                details.listed().fold(() -> open.remove(listed._1), now -> open.put(listed._1, now)))
                        .getOrElse(open)),
                read);
    }

    /**
     * This state with the open pull requests {@code open} as read {@code read} read them: a merge that failed before
     * that read started no longer shows once one of the pull requests it merges no longer stands as it did then, as
     * merging it again may go now (ADR 119).
     */
    private GitHubState withOpen(Option<Map<BranchName, PullRequest>> open, long read) {
        GitHubState now = withPullRequests(open);
        return now.withMerges(merges.filterValues(merge -> !merge.outdatedBy(now.open(), read)));
    }

    /**
     * What GitHub says the branches are built on (ADR 72, 169): by the branch an open pull request merges from, the
     * branch below it in its stack, else the base it merges into; and the protected branches, which are roots, as the
     * main branch is. All are branches as GitHub names them, so that this changes only when GitHub's answer does.
     */
    public StatedBases bases() {
        return new StatedBases(
                open().flatMap(pr -> base(pr)
                                // A pull request from a fork's branch into the project's of the same name, as main
                                // into main, says nothing of what a branch is built on.
                                .filter(base -> !base.equals(FromGitHub.branch(pr.headBranch())))
                                .map(base -> Tuple.of(
                                        FromGitHub.branch(pr.headBranch()),
                                        new StatedBases.Stated(
                                                base,
                                                Phrase.of(
                                                        "github.bases.pullRequest",
                                                        String.valueOf(
                                                                pr.number().value()))))))
                        .toMap(pair -> pair),
                protectedBranches.getOrElse(HashSet.empty()),
                branchChecks.map(GitHubChecks::remotes));
    }

    /**
     * The branch {@code pr} is built on: the one below it in its stack, else its base; none when GitHub did not say.
     */
    private static Option<BranchName> base(PullRequest pr) {
        return pr.stack()
                .flatMap(PullRequestStack::branchBelow)
                .orElse(pr::baseBranch)
                .map(FromGitHub::branch);
    }

    /**
     * The open pull requests, as read last; none until they were read.
     */
    private List<PullRequest> open() {
        return pullRequests.map(byBranch -> byBranch.values().toList()).getOrElse(List.empty());
    }

    /**
     * The open pull request from {@code branch}: the one from the branch it follows on the remote, else from
     * the branch of the same name; none when it follows a branch on a remote other than GitHub.
     */
    public Option<PullRequest> pullRequest(Branch branch) {
        Option<RemoteBranch> upstream = branch.remoteUpstream();
        RemoteName pushedTo = branch.pushTarget().remote();
        return upstream.exists(followed -> !mayBeOnGitHub(followed))
                ? Option.none()
                : pullRequests
                        .flatMap(open -> upstream.map(RemoteBranch::branch)
                                .flatMap(open::get)
                                .orElse(() -> open.get(branch.name())))
                        .filter(pr -> from(pr, pushedTo));
    }

    /**
     * The open pull request from {@code remote}, if it is a branch on GitHub.
     */
    public Option<PullRequest> pullRequest(RemoteBranch remote) {
        return onGitHub(remote)
                ? pullRequests.flatMap(open -> open.get(remote.branch())).filter(pr -> from(pr, remote.remote()))
                : Option.none();
    }

    /**
     * Whether the branch of {@code pr} is in the repository of {@code remote}, as far as is known (ADR 177): one
     * whose remotes are not known matches by its name alone.
     */
    private boolean from(PullRequest pr, RemoteName remote) {
        return heads.get(pr.number()).forAll(in -> in.contains(remote));
    }

    /**
     * Whether the branch of {@code pr} is in a fork, not in the repository.
     */
    public boolean fromFork(PullRequest pr) {
        return forks.contains(pr.number());
    }

    /**
     * How the CI checks are doing on the branch {@code branch} follows on GitHub, if it follows one.
     */
    public Option<Checks> checks(Branch branch) {
        return branch.remoteUpstream().flatMap(this::checks);
    }

    /**
     * How the CI checks are doing on {@code remote}, if it is a branch on GitHub.
     */
    public Option<Checks> checks(RemoteBranch remote) {
        return branchChecks
                .filter(checks -> checks.remotes().contains(remote.remote()))
                .flatMap(checks -> checks.byBranch().get(remote.branch()));
    }

    /**
     * When the checks on the branch {@code branch} follows on GitHub are expected to be done, while they are pending
     * and were read one by one (ADR 32).
     */
    public Option<Eta> eta(Branch branch) {
        return branch.remoteUpstream().flatMap(this::eta);
    }

    /**
     * When the checks on {@code remote}, a branch on GitHub, are expected to be done, while they are pending and were
     * read one by one (ADR 32).
     */
    public Option<Eta> eta(RemoteBranch remote) {
        return checks(remote)
                .filter(state -> state == Checks.PENDING)
                .flatMap(state -> pendingChecks)
                .flatMap(pending -> pending.byBranch()
                        .get(remote.branch())
                        .map(checks -> checkTimes
                                .filter(times -> times.repository().equals(pending.repository()))
                                .map(times -> times.eta(checks))
                                .getOrElse(() -> Eta.of(checks, CheckHistory.EMPTY))));
    }

    /**
     * That the checks on the branch {@code branch} follows on GitHub all wait to start, while they are pending and
     * were read one by one (ADR 35).
     */
    public Option<Waiting> waiting(Branch branch) {
        return branch.remoteUpstream().flatMap(this::waiting);
    }

    /**
     * That the checks on {@code remote}, a branch on GitHub, that are not done all wait to start, while they are
     * pending and were read one by one (ADR 35): none while one of them runs.
     */
    public Option<Waiting> waiting(RemoteBranch remote) {
        return checks(remote)
                .filter(state -> state == Checks.PENDING)
                .flatMap(state -> pendingChecks)
                .flatMap(pending -> pending.byBranch().get(remote.branch()))
                .map(checks -> checks.filter(check -> !check.done()))
                .filter(notDone -> notDone.nonEmpty() && notDone.forAll(Check::queued))
                .map(Waiting::of);
    }

    /**
     * Whether the checks that ran lately are being read from GitHub, to estimate from.
     */
    public boolean learningCheckTimes() {
        return checkTimes.exists(CheckTimes::toppingUp);
    }

    private boolean onGitHub(RemoteBranch remote) {
        return branchChecks.exists(checks -> checks.remotes().contains(remote.remote()));
    }

    /**
     * Whether {@code remote} is on GitHub, or could be, as which remotes are is not known yet.
     */
    public boolean mayBeOnGitHub(RemoteBranch remote) {
        return branchChecks.isEmpty() || onGitHub(remote);
    }

    /**
     * The read of the open pull requests of the repository, which GitHub's feature declares (ADR 168): one at a time
     * runs, and each that starts gets an id (ADR 119).
     */
    public interface ListRead extends Cmd {}
}
