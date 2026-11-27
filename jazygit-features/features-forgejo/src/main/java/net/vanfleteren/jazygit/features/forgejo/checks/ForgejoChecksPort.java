package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPort;
import net.vanfleteren.jazygit.features.forge.checks.ChecksRead;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck;
import net.vanfleteren.jazygit.features.forge.checks.RerunPlan;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoBuilds;
import net.vanfleteren.jazygit.features.forgejo.show.ForgejoPullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * What Forgejo gives the checks flow (ADR 195, 209): its checks are the statuses of a commit, read by commit, of which
 * those of Forgejo Actions link to their job; it does not tell how long they take, and runs failed jobs again one at a
 * time.
 */
public enum ForgejoChecksPort implements ChecksPort {
    PORT;

    @Override
    public String forge() {
        return "Forgejo";
    }

    /**
     * The checks of the tip {@code branch} has, or the one it follows has.
     */
    @Override
    public Option<Opening> ofBranch(Model model, BranchRef branch) {
        return onForgejo(model, branch)
                .flatMap(remote ->
                        tip(model, remote).map(tip -> new Opening(branch, remote, Option.of(tip), Option.none())));
    }

    /**
     * The checks of the commit the pull request of the open dialog merges, as Forgejo said, which its remote branch may
     * not have caught up with; Escape goes back to the dialog.
     */
    @Override
    public Option<Opening> ofPullRequest(Model model) {
        return model.popup(ForgejoPullRequestPopup.class)
                .filter(ForgejoChecksPort::hasChecks)
                .flatMap(dialog -> dialog.shown()
                        .map(shown -> new Opening(
                                new BranchRef.Local(dialog.local()),
                                dialog.branch(),
                                Option.of(shown.pullRequest().branches().headSha()),
                                Option.of(dialog))));
    }

    /**
     * Reads the checks again: those of a branch on the tip it has now, as after a fetch or a push; those of a pull
     * request on the commit it merges.
     */
    @Override
    public Cmd load(Model model, ChecksPopup popup) {
        Option<Sha> tip = popup.from().isDefined()
                ? popup.commit()
                : tip(model, popup.onForge()).orElse(popup.commit());
        return new LoadChecks(popup.onForge(), tip);
    }

    /**
     * The checks in the order they are listed: the failed ones first, then the running ones, the queued ones and the
     * passed ones, each in the order they were first posted.
     */
    @Override
    public List<ForgeCheck> ordered(Model model, ChecksPopup popup) {
        return popup.checksRead().sortBy(ForgejoChecksPort::rank);
    }

    /**
     * None: as Forgejo does not tell how long the checks take, how far they are counts those done, which never goes
     * back.
     */
    @Override
    public Option<ChecksPopup.Reached> reached(Model model, ChecksPopup popup) {
        return Option.none();
    }

    @Override
    public Option<OperationCmd> log(ChecksPopup popup, ForgeCheck check) {
        return source(check).flatMap(ForgejoCheck::job).map(job -> new CheckLogCmd(popup.onForge(), check.name(), job));
    }

    @Override
    public OperationCmd rerun(ChecksPopup popup, List<RerunPlan.Run> runs) {
        return new RerunCmd(popup.onForge(), runs);
    }

    @Override
    public Next loaded(Next next, RemoteBranch branch, ChecksRead read) {
        return next;
    }

    /**
     * The builds of the branches are read again, as a commit read before may build again.
     */
    @Override
    public Next reran(Next next) {
        return next.map(model -> model.mapPart(ForgejoBuilds.PART, ForgejoBuilds::fetched));
    }

    /**
     * {@code check}, as the checks flow reads it: a job of Forgejo Actions when its status links to one, its workflow
     * and event as its status names them, {@code <workflow> / <job> (<event>)}.
     */
    public static ForgeCheck of(ForgejoCheck check) {
        return new ForgeCheck(
                check.name(),
                switch (check.state()) {
                    case FAILED -> ForgeCheck.State.FAILED;
                    case PASSED -> check.skipped() ? ForgeCheck.State.SKIPPED : ForgeCheck.State.PASSED;
                    case RUNNING, NONE -> ForgeCheck.State.PENDING;
                },
                check.job().map(job -> {
                    Context context = Context.of(check.name());
                    return new ForgeCheck.Job(
                            job.run(), Option.of((long) job.job()), context.workflow(), context.event());
                }),
                new Read(check));
    }

    /**
     * The statuses of Forgejo that {@code checks} are, in their order.
     */
    public static List<ForgejoCheck> checks(List<ForgeCheck> checks) {
        return checks.flatMap(ForgejoChecksPort::source);
    }

    /**
     * Whether the pull request {@code dialog} shows has checks to list: once its details are read, when the commit it
     * merges has statuses.
     */
    public static boolean hasChecks(ForgejoPullRequestPopup dialog) {
        return dialog.shown().exists(shown -> !shown.checks().statuses().isEmpty());
    }

    /**
     * The branch on Forgejo whose checks {@code branch} shows: itself when remote, else the one it follows; none when
     * that is not on Forgejo, or has no checks, as its badge tells.
     */
    private static Option<RemoteBranch> onForgejo(Model model, BranchRef branch) {
        ForgejoBuilds builds = model.part(ForgejoBuilds.PART);
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
                .filter(remote -> !builds.elsewhere().contains(remote.remote()))
                .filter(remote ->
                        tip(model, remote).flatMap(builds::state).exists(state -> state != CommitStatus.State.NONE));
    }

    /**
     * The commit {@code branch} points to, as far as the repository knows it.
     */
    private static Option<Sha> tip(Model model, RemoteBranch branch) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .flatMap(refs -> refs.find(ref -> ref.branch().equals(branch)))
                .map(RemoteRef::tip);
    }

    private static int rank(ForgeCheck check) {
        return check.failed() ? 0 : !check.done() ? (source(check).exists(ForgejoCheck::queued) ? 2 : 1) : 3;
    }

    private static Option<ForgejoCheck> source(ForgeCheck check) {
        return check.source() instanceof Read(ForgejoCheck read) ? Option.of(read) : Option.none();
    }

    /**
     * A status as Forgejo read it.
     */
    public record Read(ForgejoCheck check) implements ForgeCheck.Source {}

    /**
     * What Forgejo Actions names a job's status, {@code <workflow> / <job> (<event>)}.
     */
    private record Context(String workflow, String event) {

        static Context of(String name) {
            int slash = name.indexOf(" / ");
            int open = name.lastIndexOf(" (");
            return new Context(
                    slash > 0 ? name.substring(0, slash) : name,
                    open > 0 && name.endsWith(")") ? name.substring(open + 2, name.length() - 1) : "");
        }
    }
}
