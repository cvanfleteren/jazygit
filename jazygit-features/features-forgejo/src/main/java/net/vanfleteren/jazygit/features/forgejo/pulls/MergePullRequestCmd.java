package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Merges pull request {@code number} of the home {@code home} on Forgejo as {@code style} says, at once (ADR 180):
 * only as its head stood when read, as {@code standing} says, and deleting its head branch when {@code deleteBranch}
 * says so, as its repository does by default. {@code branch}, the local branch it was shown for, is busy meanwhile.
 */
public record MergePullRequestCmd(
        RemoteName home,
        BranchName branch,
        long number,
        ForgejoMergeStyle style,
        FailedMerge.Standing standing,
        boolean deleteBranch)
        implements OperationCmd {

    static final Operation MERGE = Operation.of("forgejo.log.title.mergePullRequest", "forgejo.error.merge.failed")
            .labelledWhenBusy("forgejo.branches.busy.merge");

    @Override
    public Operation operation() {
        return MERGE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(branch.value());
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return Option.of(new BranchRef.Local(branch));
    }

    // Its base changed, and it is no longer open: the pull requests are read again, as after any change to the forge.
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
                                repository, number, new ForgejoMerge(style, standing.head(), deleteBranch))
                        .<Msg>fold(
                                error -> failed(new MergeFailure(error, repository.host(), named(repository))),
                                merged -> new PullRequestMsg.Merged(this, merged)))
                .getOrElse(() -> failed(new ForgejoFailure.NoHome()));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.MergeFailed(this, failure);
    }

    private static String named(ForgejoRepository repository) {
        return repository.owner() + "/" + repository.name();
    }
}
