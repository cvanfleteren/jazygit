package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.ChangeFailed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Changed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Tick;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.StackChange;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * The stacks of pull requests (ADR 72, 73, 74, 99): each group of {@link StackMsg} goes to its own update, showing
 * them to {@link StackMenuUpdate}, making and changing one to {@link StackEditUpdate}, rebasing and pushing one to
 * {@link StackRebaseUpdate}, checking one out to {@link StackCheckoutUpdate}. Waiting for GitHub to show a change made
 * to the stacks is done here.
 */
public final class StackUpdate {

    // How long the pull requests are read again after a change to the stacks, while GitHub does not list it yet.
    static final Duration SETTLING = Duration.ofSeconds(30);

    private StackUpdate() {}

    public static Next update(Model model, StackMsg msg) {
        return switch (msg) {
            case StackMsg.Showing showing -> StackMenuUpdate.update(model, showing);
            case StackMsg.Editing editing -> StackEditUpdate.update(model, editing);
            case StackMsg.Rebasing rebasing -> StackRebaseUpdate.update(model, rebasing);
            case StackMsg.CheckingOut checkingOut -> StackCheckoutUpdate.update(model, checkingOut);
            case Changed(StackCmd cmd, List<ExecutedCommand> commands) -> changed(model, cmd, commands);
            case ChangeFailed(StackCmd cmd, Failure failure) -> changeFailed(model, cmd, failure);
            case Tick() -> settling(model);
        };
    }

    /**
     * Once {@code cmd} changed the stacks: the pull requests are read again until they show the change.
     */
    private static Next changed(Model model, StackCmd cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.mapPart(
                        GitHubState.PART,
                        gitHub -> gitHub.withStackChange(
                                Option.of(cmd.change(model.now().plus(SETTLING))))));
    }

    /**
     * What it changed before it failed shows: the pull requests are read again.
     */
    private static Next changeFailed(Model model, StackCmd cmd, Failure failure) {
        Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
        return failure.commands().isEmpty() ? failed : PullRequestReads.loadAnyway(failed);
    }

    /**
     * Reads the pull requests again while they do not show the change made to the stacks yet, until they do or it is
     * given up on: GitHub can list them as they were for a while after it made the change (ADR 73).
     */
    private static Next settling(Model model) {
        GitHubState gitHub = model.part(GitHubState.PART);
        return gitHub.stackChange()
                .map(change -> {
                    Model settled = model.withPart(GitHubState.PART, gitHub.withStackChange(Option.none()));
                    if (gitHub.pullRequests().exists(change::shownIn)) {
                        return change instanceof StackChange.Stacked stacked && stacked.rebase()
                                ? StackRebaseUpdate.rebaseListed(settled, stacked)
                                : Next.of(settled);
                    }
                    if (!model.now().isAfter(change.until())) {
                        return PullRequestReads.load(Next.of(model));
                    }
                    // A rebase asked for when linking is not done: the user is told, to do it from the menu.
                    return change instanceof StackChange.Stacked stacked && stacked.rebase()
                            ? Next.of(settled.inform(new StackNotice.NotListed(
                                    stacked.pullRequests().head())))
                            : Next.of(settled);
                })
                .getOrElse(() -> Next.of(model));
    }
}
