package net.vanfleteren.jazygit.features.agent;

import net.vanfleteren.jazygit.features.agent.investigate.InvestigateAction;
import net.vanfleteren.jazygit.features.agent.settings.AgentForm;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsRows;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;

/**
 * Handing what failed to a coding agent the user set (ADR 161). It knows no forge: it reaches a failed build only
 * through the keys of {@code Scopes.FAILED_BUILD}, which the views of a forge host. It keeps the agents in the
 * settings file (ADR 163), and the settings panel edits them (ADR 164).
 */
public final class AgentFeature {

    /**
     * The agents in the settings panel: a row that opens their list.
     */
    public static final SettingsSection SECTION =
            SettingsSection.of("agent.settings.group", SettingsRows.list('i', "agent.settings.row", AgentForm.LIST));

    public static final Feature FEATURE =
            Feature.of(InvestigateAction.ACTION).keeping(Agents.SETTING).withSettings(SECTION);

    private AgentFeature() {}
}
