package net.vanfleteren.jazygit.features.agent.investigate;

import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.kernel.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why a failed build could not be handed to an agent (ADR 161): what {@code I} says instead of doing nothing.
 */
public sealed interface InvestigateError extends AppError {

    /**
     * The settings file sets no agent.
     */
    record NoAgentSet() implements InvestigateError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "agent.error.investigate.noAgent",
                    // On lines of their own, as TOML has them, so it can be pasted as it is.
                    "[[agents]]\nname = \"Claude\"\ncommand = [\"claude\", \"" + AgentProgram.PROMPT
                            + "\"]\nterminal = true");
        }
    }

    /**
     * Nothing failed in the build: its jobs passed, or the failed ones are not known yet.
     */
    record NothingFailed() implements InvestigateError {

        @Override
        public Phrase phrase() {
            return Phrase.of("agent.error.investigate.nothingFailed");
        }
    }

    /**
     * Nothing failed in the build yet, while some of its jobs still run.
     */
    record StillRunning() implements InvestigateError {

        @Override
        public Phrase phrase() {
            return Phrase.of("agent.error.investigate.stillRunning");
        }
    }

    /**
     * No worktree has the branch checked out, so the agent has nowhere to work on it.
     */
    record NotInWorktree(BranchName branch) implements InvestigateError {

        @Override
        public Phrase phrase() {
            return Phrase.of("agent.error.investigate.notInWorktree", branch.value());
        }
    }
}
