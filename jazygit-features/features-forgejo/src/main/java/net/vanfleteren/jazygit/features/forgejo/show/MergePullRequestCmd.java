package net.vanfleteren.jazygit.features.forgejo.show;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoRemotes;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Merges pull request {@code number} of the home {@code home} on Forgejo as {@code style} says (ADR 180): only as its
 * head stood when read, as {@code standing} says, and deleting its head branch when {@code deleteBranch} says so, as
 * its repository does by default. {@code branch}, the local branch it was shown for, is busy meanwhile.
 *
 * <p>When {@code waitsFor} holds what its base branch requires, with the {@code approvals} it had, it is scheduled for
 * when its checks succeed instead (ADR 181), unless the checks of its head passed by now: Forgejo would wait for
 * something else to happen then, and it is merged now. That merge still runs as the scheduling it was chosen as: it
 * is logged, shown busy and told when it fails as one, and the dialog stays open, to show it merged once read again.
 */
public record MergePullRequestCmd(
        RemoteName home,
        BranchName branch,
        long number,
        ForgejoMergeStyle style,
        FailedMerge.Standing standing,
        boolean deleteBranch,
        Option<ForgejoBranchRules> waitsFor,
        int approvals)
        implements OperationCmd {

    static final Operation MERGE = Operation.of("forgejo.log.title.mergePullRequest", "forgejo.error.merge.failed")
            .labelledWhenBusy("forgejo.branches.busy.merge");

    static final Operation SCHEDULE = Operation.of("forgejo.log.title.scheduleMerge", "forgejo.error.schedule.failed")
            .labelledWhenBusy("forgejo.branches.busy.schedule");

    // What Forgejo says, with a 409, of a pull request whose merge is scheduled already.
    private static final String ALREADY = "already scheduled";

    /**
     * A merge now.
     */
    public MergePullRequestCmd(
            RemoteName home,
            BranchName branch,
            long number,
            ForgejoMergeStyle style,
            FailedMerge.Standing standing,
            boolean deleteBranch) {
        this(home, branch, number, style, standing, deleteBranch, Option.none(), 0);
    }

    /**
     * Whether it is scheduled for when its checks succeed, rather than merged now, as far as was read; it is merged
     * now all the same when its checks passed by the time it is sent, which its operation does not tell.
     */
    public boolean whenChecksSucceed() {
        return waitsFor.isDefined();
    }

    @Override
    public Operation operation() {
        return whenChecksSucceed() ? SCHEDULE : MERGE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(branch.value());
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return Option.of(new BranchRef.Local(branch));
    }

    // Its base changed, and it is no longer open; or it is scheduled: the pull requests are read again, as after any
    // change to the forge.
    @Override
    public boolean changesForge() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return ForgejoRemotes.of(env, forgejo, home)
                .map(ForgejoRemotes::home)
                .map(repository -> forgejo.merge(
                                repository,
                                number,
                                new ForgejoMerge(style, standing.head(), deleteBranch, stillWaits(forgejo, repository)))
                        .<Msg>fold(
                                error -> refused(error, repository), merged -> new PullRequestMsg.Merged(this, merged)))
                .getOrElse(() -> failed(new ForgejoFailure.NoHome()));
    }

    /**
     * Whether it is still to wait for its checks: the checks of its head are read again, as they may have passed
     * since it was shown. When they cannot be read, it waits as was read.
     */
    private boolean stillWaits(Forgejo forgejo, ForgejoRepository repository) {
        return waitsFor.exists(rules -> forgejo.status(repository, standing.head())
                .map(status -> rules.waitFor(status, approvals))
                .getOrElse(true));
    }

    private Msg refused(ForgejoError error, ForgejoRepository repository) {
        return error instanceof ForgejoError.Refused refused
                        && refused.status() == ForgejoError.Refused.CONFLICT
                        && refused.stated().exists(message -> message.contains(ALREADY))
                ? new PullRequestMsg.AlreadyScheduled(this)
                : failed(new MergeFailure(error, repository.host(), named(repository)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.MergeFailed(this, failure);
    }

    static String named(ForgejoRepository repository) {
        return repository.owner() + "/" + repository.name();
    }
}
