package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPort;
import net.vanfleteren.jazygit.features.forge.checks.ChecksRead;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck;
import net.vanfleteren.jazygit.features.forge.checks.RerunPlan;
import net.vanfleteren.jazygit.features.github.checks.branches.BranchChecksUpdate;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimes;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimesUpdate;
import net.vanfleteren.jazygit.features.github.checks.timings.ChecksReached;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.state.FromGitHub;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * What GitHub gives the checks flow (ADR 209): its checks are read by branch, with how long they take, which it learns
 * from each read (ADR 32), and its failed workflow runs are run again a run at a time (ADR 135).
 */
public enum GitHubChecksPort implements ChecksPort {
    PORT;

    @Override
    public String forge() {
        return "GitHub";
    }

    @Override
    public Option<Opening> ofBranch(Model model, BranchRef branch) {
        return onGitHub(model, branch).map(remote -> new Opening(branch, remote, Option.none(), Option.none()));
    }

    /**
     * The checks of the pull request of the pull request dialog that is open, which Escape goes back to.
     */
    @Override
    public Option<Opening> ofPullRequest(Model model) {
        return model.popup(PullRequestPopup.class)
                .flatMap(dialog -> pullRequestBranch(model, dialog)
                        .map(remote -> new Opening(remote.tracking(), remote, Option.none(), Option.of(dialog))));
    }

    @Override
    public Cmd load(Model model, ChecksPopup popup) {
        return new LoadChecks(popup.onForge());
    }

    /**
     * The checks in the order they are listed, as far as {@code model} tells how long they take: the failed ones
     * first.
     */
    @Override
    public List<ForgeCheck> ordered(Model model, ChecksPopup popup) {
        List<Check> all = checks(popup.checksRead());
        return CheckTimes.order(all, all, times(model, popup)).map(GitHubChecksPort::of);
    }

    /**
     * How far the checks are shown to be in {@code model}, as reached by those same checks; nothing once they are done,
     * as they are then shown full anyway, nor when they are only commit statuses, which no workflow run tells apart
     * from those of another commit, and which are counted, so do not go back (ADR 89).
     */
    @Override
    public Option<ChecksPopup.Reached> reached(Model model, ChecksPopup popup) {
        return ChecksReached.keeping(checks(popup.checksRead()), progress(model, popup))
                .map(ChecksPopup.Reached.class::cast);
    }

    @Override
    public Option<OperationCmd> log(ChecksPopup popup, ForgeCheck check) {
        return source(check)
                .flatMap(Check::run)
                .flatMap(run -> popup.read().map(read -> new CheckLogCmd(read.repository(), check.name(), run)));
    }

    @Override
    public OperationCmd rerun(ChecksPopup popup, List<RerunPlan.Run> runs) {
        return new RerunCmd(popup.onForge(), runs);
    }

    /**
     * How long checks take is learned from them too, and the branch catches up when it shows them otherwise.
     */
    @Override
    public Next loaded(Next next, RemoteBranch branch, ChecksRead read) {
        List<Check> checks = checks(read.checks());
        return CheckTimesUpdate.read(caughtUp(next, branch, checks), read.repository(), checks);
    }

    /**
     * The branches read their checks again too.
     */
    @Override
    public Next reran(Next next) {
        return BranchChecksUpdate.loadAnyway(next);
    }

    /**
     * {@code check}, as the checks flow reads it: a job of GitHub Actions when it is of a workflow run.
     */
    public static ForgeCheck of(Check check) {
        return new ForgeCheck(
                check.name(),
                switch (check.state()) {
                    case FAILING -> ForgeCheck.State.FAILED;
                    case PASSING ->
                        check.run().exists(run -> run.conclusion().equals("SKIPPED"))
                                ? ForgeCheck.State.SKIPPED
                                : ForgeCheck.State.PASSED;
                    case PENDING, NONE -> ForgeCheck.State.PENDING;
                },
                check.run().map(GitHubChecksPort::job),
                new Read(check));
    }

    /**
     * The checks of GitHub that {@code checks} are, in their order.
     */
    public static List<Check> checks(List<ForgeCheck> checks) {
        return checks.flatMap(GitHubChecksPort::source);
    }

    /**
     * How long the checks of {@code popup} take, as learned in {@code model}, of the repository they are of; nothing
     * while they are first read, or when that failed.
     */
    public static Option<CheckTimes> times(Model model, ChecksPopup popup) {
        return popup.read()
                .flatMap(read -> model.part(GitHubState.PART)
                        .checkTimes()
                        .filter(known -> known.repository().equals(read.repository())));
    }

    /**
     * How far the checks of {@code popup} are together in {@code model}: at least as far as they were reached to be,
     * while they are the same checks, of the same attempts of the same workflow runs. A workflow run again, or the
     * checks of another commit, start from 0.
     */
    public static double progress(Model model, ChecksPopup popup) {
        return ChecksReached.progress(
                popup.reached().filter(ChecksReached.class::isInstance).map(ChecksReached.class::cast),
                checks(popup.checksRead()),
                times(model, popup),
                model.now());
    }

    /**
     * The branch on GitHub the pull request of {@code dialog} merges from, whose checks it shows, once its details are
     * read; none when it has no checks, or comes from a fork, whose branch is not one of this repository, though one
     * here may have its name. Every remote on GitHub is of the same repository, so has the same checks.
     */
    public static Option<RemoteBranch> pullRequestBranch(Model model, PullRequestPopup dialog) {
        BranchName head = FromGitHub.branch(dialog.pullRequest().headBranch());
        GitHubState gitHub = model.part(GitHubState.PART);
        return gitHub.branchChecks()
                .filter(checks -> dialog.detailsRead() && !dialog.fromFork())
                .toList()
                .flatMap(checks -> checks.remotes().toList().sorted())
                .map(remote -> new RemoteBranch(remote, head))
                .find(remote -> gitHub.checks(remote).exists(state -> state != Checks.NONE));
    }

    /**
     * The branch on GitHub whose checks {@code branch} shows: itself when remote, else the one it follows; none when
     * that is not on GitHub, or has no checks.
     */
    private static Option<RemoteBranch> onGitHub(Model model, BranchRef branch) {
        return (switch (branch) {
                    case BranchRef.Local(BranchName name) ->
                        model.repo()
                                .branches()
                                .toOption()
                                .flatMap(branches ->
                                        branches.find(local -> local.name().equals(name)))
                                .flatMap(Branch::remoteUpstream);
                    case BranchRef.Remote(RemoteBranch remote) -> Option.of(remote);
                })
                .filter(remote -> model.part(GitHubState.PART).checks(remote).exists(state -> state != Checks.NONE));
    }

    /**
     * {@code next}, also reading the checks of the branches again when {@code read}, the checks on {@code branch} just
     * read one by one, together do otherwise than the branch shows; unchanged when none were read, or {@code branch} is
     * not on GitHub. The branch takes how they do from that read rather than from {@code read}: the reads of the
     * branch checks run one at a time, so a later one cannot be replaced by an earlier one, while {@code read} may
     * have started before the read the branch shows, and be of a tip since pushed over.
     */
    private static Next caughtUp(Next next, RemoteBranch branch, List<Check> read) {
        return read.isEmpty()
                        || next.model()
                                .part(GitHubState.PART)
                                .checks(branch)
                                .forAll(shown -> shown == Checks.combined(read.map(Check::state)))
                ? next
                : BranchChecksUpdate.loadAnyway(next);
    }

    private static ForgeCheck.Job job(CheckRun run) {
        // A check of a run that is only queued has no job yet.
        return new ForgeCheck.Job(run.runId(), Option.when(run.id() != 0, run::id), run.workflow(), run.event());
    }

    private static Option<Check> source(ForgeCheck check) {
        return check.source() instanceof Read(Check read) ? Option.of(read) : Option.none();
    }

    /**
     * A check as GitHub read it.
     */
    public record Read(Check check) implements ForgeCheck.Source {}
}
