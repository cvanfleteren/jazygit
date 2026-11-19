package net.vanfleteren.jazygit.features.agent.investigate;

import net.vanfleteren.jazygit.kernel.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;

/**
 * Handing a failed build to an agent (ADR 161).
 */
public sealed interface InvestigateMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return InvestigateUpdate.update(model, this);
    }

    /**
     * The user asked an agent to investigate {@code build}.
     */
    record Requested(FailedBuild build) implements InvestigateMsg {}

    /**
     * The user chose {@code agent} in the menu of the agents.
     */
    record Chosen(AgentProgram agent) implements InvestigateMsg {}
}
