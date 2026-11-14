package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * While the terminal does not have the focus, the timers read less (ADR 67); once it has it again, what they asked
 * for meanwhile is read once (ADR 58).
 */
class FocusUpdateTest {

    @Test
    void awayATickReadsNothing() {
        Model away = Update.update(loaded(), new TerminalMsg.Focused(false)).model();

        Next ticked = Update.update(away, new TimerMsg.Tick());

        assertEquals(List.empty(), ticked.cmds());
        assertTrue(ticked.model().jobs().away());
    }

    @Test
    void backAgainWhatTheTimersAskedForMeanwhileIsReadOnce() {
        Model away = Update.update(loaded(), new TerminalMsg.Focused(false)).model();
        away = Update.update(away, new TimerMsg.Tick()).model();
        away = Update.update(away, new FetchMsg.Tick()).model();
        away = Update.update(away, new TimerMsg.Tick()).model();

        Next back = Update.update(away, new TerminalMsg.Focused(true));

        Next expected =
                Update.update(Update.update(loaded(), new TimerMsg.Tick()).model(), new FetchMsg.Tick());
        assertEquals(Update.update(loaded(), new TimerMsg.Tick()).cmds().appendAll(expected.cmds()), back.cmds());
        assertFalse(back.model().jobs().away());
        assertTrue(back.model().jobs().missed().isEmpty());
    }

    @Test
    void whatTheUserDoesIsHandledWhileAway() {
        Model away = Update.update(loaded(), new TerminalMsg.Focused(false)).model();

        assertEquals(
                Update.update(loaded(), new TimerMsg.Clock()).model().screen(),
                Update.update(away, new TimerMsg.Clock()).model().screen());
        assertTrue(Update.update(away, new Msg.Quit()).quit());
    }

    @Test
    void backWithoutAnyTimerMeanwhileReadsNothing() {
        Model away = Update.update(loaded(), new TerminalMsg.Focused(false)).model();

        assertEquals(
                List.empty(), Update.update(away, new TerminalMsg.Focused(true)).cmds());
    }

    private static final Instant AWAY_AT = Instant.parse("2026-10-31T20:00:00Z");

    @Test
    void awayATickIsHandledOnceItWaited() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();
        away = Update.update(away, new TimerMsg.Tick()).model();
        away = Update.update(at(away, TimerMsg.Tick.WHILE_AWAY.minusSeconds(1)), new TimerMsg.Tick())
                .model();

        Next ticked = Update.update(at(away, TimerMsg.Tick.WHILE_AWAY), new TimerMsg.Tick());

        assertEquals(
                Update.update(at(loaded().withNow(AWAY_AT), TimerMsg.Tick.WHILE_AWAY), new TimerMsg.Tick())
                        .cmds(),
                ticked.cmds());
        assertFalse(ticked.cmds().isEmpty());
        assertTrue(ticked.model().jobs().away());
        assertFalse(ticked.model().jobs().missed().contains(new TimerMsg.Tick()));
    }

    @Test
    void awayATickWaitsAnewOnceHandled() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();
        away = Update.update(away, new TimerMsg.Tick()).model();
        away = Update.update(at(away, TimerMsg.Tick.WHILE_AWAY), new TimerMsg.Tick())
                .model();

        Next ticked =
                Update.update(at(away, TimerMsg.Tick.WHILE_AWAY.multipliedBy(2).minusSeconds(1)), new TimerMsg.Tick());

        assertEquals(List.empty(), ticked.cmds());
        assertTrue(ticked.model().jobs().missed().contains(new TimerMsg.Tick()));
    }

    @Test
    void awayAFetchWaitsItsOwnWhile() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();
        away = Update.update(away, new FetchMsg.Tick()).model();

        assertEquals(
                List.empty(),
                Update.update(at(away, TimerMsg.Tick.WHILE_AWAY), new FetchMsg.Tick())
                        .cmds());
        Next fetched = Update.update(at(away, FetchMsg.Tick.WHILE_AWAY), new FetchMsg.Tick());
        assertEquals(
                Update.update(at(loaded().withNow(AWAY_AT), FetchMsg.Tick.WHILE_AWAY), new FetchMsg.Tick())
                        .cmds(),
                fetched.cmds());
        assertFalse(fetched.cmds().isEmpty());
        assertFalse(fetched.model().jobs().missed().contains(new FetchMsg.Tick()));
    }

    @Test
    void awayATickThatComesAFewMillisecondsEarlyIsHandled() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();
        away = Update.update(away, new TimerMsg.Tick()).model();

        assertFalse(Update.update(at(away, TimerMsg.Tick.WHILE_AWAY.minusMillis(20)), new TimerMsg.Tick())
                .cmds()
                .isEmpty());
    }

    @Test
    void losingTheFocusAgainDoesNotStartTheWaitsAnew() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();
        away = Update.update(away, new TimerMsg.Tick()).model();
        away = Update.update(at(away, Duration.ofSeconds(10)), new TerminalMsg.Focused(false))
                .model();

        assertFalse(Update.update(at(away, TimerMsg.Tick.WHILE_AWAY), new TimerMsg.Tick())
                .cmds()
                .isEmpty());
    }

    @Test
    void awayWhatTheTimersKeepIsNotDrawn() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new TerminalMsg.Focused(false))
                .model();

        assertEquals(
                away.drawn(), Update.update(away, new TimerMsg.Tick()).model().drawn());
    }

    private static Model at(Model model, Duration later) {
        return model.withNow(AWAY_AT.plus(later));
    }
}
