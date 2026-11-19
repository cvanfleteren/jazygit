package net.vanfleteren.jazygit.features.agent.investigate;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the agents the user set investigates {@code build} (ADR 161).
 *
 * @param build    what failed
 * @param worktree where the worktree that has its branch is, as text, which the agent runs in
 * @param agents   each agent with the letter that chooses it, in the order of the settings file
 */
public record InvestigatePopup(FailedBuild build, String worktree, List<Tuple2<Character, AgentProgram>> agents)
        implements Popup {}
