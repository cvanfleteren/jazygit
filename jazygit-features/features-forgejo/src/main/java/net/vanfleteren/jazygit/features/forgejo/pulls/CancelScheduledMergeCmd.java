package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Cancels the merge of pull request {@code number} of the home {@code home} on Forgejo scheduled for when its checks
 * succeed (ADR 181). {@code branch}, the local branch it was shown for, is busy meanwhile. One no longer scheduled,
 * which Forgejo answers with 404, is told so, as the timeline may say it is when Forgejo dropped it.
 */
public record CancelScheduledMergeCmd(RemoteName home, BranchName branch, long number) implements OperationCmd {

    static final Operation CANCEL = Operation.of("forgejo.log.title.cancelMerge", "forgejo.error.cancel.failed")
            .labelledWhenBusy("forgejo.branches.busy.cancel");

    private static final int NOT_FOUND = 404;

    @Override
    public Operation operation() {
        return CANCEL;
    }

    @Override
    public Option<String> subject() {
        return Option.of(branch.value());
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return Option.of(new BranchRef.Local(branch));
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return ForgejoRemotes.of(env, forgejo, home)
                .map(ForgejoRemotes::home)
                .map(repository -> forgejo.cancelScheduledMerge(repository, number)
                        .<Msg>map(
                                error -> error instanceof ForgejoError.Refused refused && refused.status() == NOT_FOUND
                                        ? new PullRequestMsg.NoLongerScheduled(this)
                                        : failed(new MergeFailure(
                                                error, repository.host(), MergePullRequestCmd.named(repository))))
                        .getOrElse(() -> new PullRequestMsg.MergeCancelled(this)))
                .getOrElse(() -> failed(new ForgejoFailure.NoHome()));
    }
}
