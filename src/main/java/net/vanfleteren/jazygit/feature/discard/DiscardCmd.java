package net.vanfleteren.jazygit.feature.discard;

import java.util.Optional;
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.discard");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new DiscardMsg.Done(git.discard(plan));
        }

        @Override
        public Msg failed(String message) {
            return new DiscardMsg.Failed(message);
        }
    }
}
