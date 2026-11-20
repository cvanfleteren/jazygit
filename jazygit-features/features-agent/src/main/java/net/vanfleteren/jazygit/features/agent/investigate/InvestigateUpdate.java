package net.vanfleteren.jazygit.features.agent.investigate;

import net.vanfleteren.jazygit.core.git.model.Worktree;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Handing a failed build to an agent (ADR 161): at once with only one set, else after choosing it in a menu of them.
 * Where it cannot be handed over, it says why.
 */
public final class InvestigateUpdate {

    private InvestigateUpdate() {}

    public static Next update(Model model, InvestigateMsg msg) {
        return switch (msg) {
            case InvestigateMsg.Requested(FailedBuild build) -> requested(model, build);
            case InvestigateMsg.Unavailable() -> Next.of(model.fail(new InvestigateError.NothingYet()));
            case InvestigateMsg.Chosen(AgentProgram agent) ->
                model.popup(InvestigatePopup.class)
                        .map(popup -> start(
                                model.withoutPopup(InvestigatePopup.class), agent, popup.build(), popup.worktree()))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Hands {@code build} to the agent set, or asks which one when there are more, in the worktree that works on its
     * branch. Without an agent, without a failed job, without such a worktree, or before the worktrees are read, it
     * says why.
     */
    private static Next requested(Model model, FailedBuild build) {
        Agents agents = model.preferences().settings().get(Agents.SETTING);
        if (agents.isEmpty()) {
            return Next.of(model.fail(new InvestigateError.NoAgentSet()));
        }
        if (!build.failed()) {
            return Next.of(model.fail(
                    build.running() ? new InvestigateError.StillRunning() : new InvestigateError.NothingFailed()));
        }
        return model.repo()
                .worktrees()
                .toOption()
                .map(worktrees -> worktrees
                        .find(worktree -> worktree.workingOn().contains(build.branch()) && !worktree.prunable())
                        .map(Worktree::location)
                        .map(location -> agents.all().size() == 1
                                ? start(model, agents.all().head(), build, location)
                                : Next.of(model.clearError()
                                        .openPopup(new InvestigatePopup(build, location, agents.lettered()))))
                        .getOrElse(() -> Next.of(model.fail(new InvestigateError.NotInWorktree(build.branch())))))
                .getOrElse(() -> Next.of(model.fail(new InvestigateError.WorktreesNotRead())));
    }

    private static Next start(Model model, AgentProgram agent, FailedBuild build, String worktree) {
        return OperationUpdate.start(model.clearError(), new InvestigateCmd(agent, worktree, build));
    }
}
