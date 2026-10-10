package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that throw changes away.
 */
public sealed interface DiscardCmd extends Cmd {

    record Discard(DiscardPlan plan) implements DiscardCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.discard(plan);
            return new DiscardMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new DiscardMsg.Failed(message);
        }
    }
}
