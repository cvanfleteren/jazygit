package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * What the terminal the app runs in reports.
 */
public sealed interface TerminalMsg extends Msg {

    /**
     * The terminal got the focus, or lost it, as a terminal that reports it says. While away, the timers read less:
     * each of their messages is handled once every {@link Msg.Periodic#whileAway()} (ADR 67); once back, what they
     * asked for since it was last handled is read once, as each of their messages is handled once (ADR 58).
     */
    record Focused(boolean focused) implements TerminalMsg {

        @Override
        public Next apply(Model model) {
            if (!focused) {
                return Next.of(model.mapJobs(
                        jobs -> jobs.away() ? jobs : jobs.withAway(true).withAwayAt(HashMap.empty())));
            }
            Set<Msg> missed = model.jobs().missed();
            return missed.foldLeft(
                    Next.of(model.mapJobs(jobs -> jobs.withAway(false)
                            .withMissed(LinkedHashSet.empty())
                            .withAwayAt(HashMap.empty()))),
                    (next, msg) -> {
                        Next handled = Update.update(next.model(), msg);
                        return new Next(handled.model(), next.cmds().appendAll(handled.cmds()));
                    });
        }
    }
}
