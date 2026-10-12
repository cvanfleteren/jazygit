package net.vanfleteren.jazygit.feature.discard;

import io.vavr.control.Option;
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
        public Option<String> logTitleKey() {
            return Option.of("log.title.discard");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.discard(plan).fold(this::failedWith, DiscardMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new DiscardMsg.Failed(message);
        }
    }
}
