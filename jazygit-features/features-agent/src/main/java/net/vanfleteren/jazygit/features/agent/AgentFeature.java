package net.vanfleteren.jazygit.features.agent;

import net.vanfleteren.jazygit.features.agent.investigate.InvestigateAction;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * Handing what failed to a coding agent the user set (ADR 161). It knows no forge: it reaches a failed build only
 * through the keys of {@code Scopes.FAILED_BUILD}, which the views of a forge host. It keeps the agents in the
 * settings file (ADR 163).
 */
public final class AgentFeature {

    public static final Feature FEATURE = Feature.of(InvestigateAction.ACTION).keeping(Agents.SETTING);

    private AgentFeature() {}
}
