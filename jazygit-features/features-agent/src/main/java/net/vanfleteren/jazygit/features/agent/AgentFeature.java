package net.vanfleteren.jazygit.features.agent;

import net.vanfleteren.jazygit.features.agent.investigate.InvestigateAction;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * Handing what failed to a coding agent the user set (ADR 161). It knows no forge: it reaches a failed build only
 * through the keys of {@code Scopes.FAILED_BUILD}, which the views of a forge host.
 */
public final class AgentFeature {

    public static final Feature FEATURE = Feature.of(InvestigateAction.ACTION);

    private AgentFeature() {}
}
