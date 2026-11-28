package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.Set;
import java.time.Duration;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TimerMsg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading which commits of the log of HEAD are not pushed yet (#722).
 */
public sealed interface UnpushedMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return UnpushedUpdate.update(model, this);
    }

    /**
     * Time to look whether HEAD, its upstream or the main branch moved without the kernel saying so, as the branches
     * are read after the log: looking reads nothing unless one did.
     */
    record Tick() implements UnpushedMsg, Msg.Periodic {

        @Override
        public Duration whileAway() {
            return TimerMsg.Tick.WHILE_AWAY;
        }
    }

    /**
     * The commits not pushed, as read for {@code tips}.
     */
    record Read(Unpushed.Tips tips, Set<Sha> commits) implements UnpushedMsg {}

    /**
     * They could not be read for {@code tips}: none is marked, as marking is only a help.
     */
    record Failed(Unpushed.Tips tips, Failure failure) implements UnpushedMsg {}
}
