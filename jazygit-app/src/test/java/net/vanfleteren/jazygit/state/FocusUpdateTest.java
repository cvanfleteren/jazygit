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
        Model away = Update.update(loaded(), new Msg.TerminalFocused(false)).model();

        Next ticked = Update.update(away, new Msg.Tick());

        assertEquals(List.empty(), ticked.cmds());
        assertTrue(ticked.model().jobs().away());
    }

    @Test
    void backAgainWhatTheTimersAskedForMeanwhileIsReadOnce() {
        Model away = Update.update(loaded(), new Msg.TerminalFocused(false)).model();
        away = Update.update(away, new Msg.Tick()).model();
        away = Update.update(away, new FetchMsg.Tick()).model();
        away = Update.update(away, new Msg.Tick()).model();

        Next back = Update.update(away, new Msg.TerminalFocused(true));

        Next expected = Update.update(Update.update(loaded(), new Msg.Tick()).model(), new FetchMsg.Tick());
        assertEquals(Update.update(loaded(), new Msg.Tick()).cmds().appendAll(expected.cmds()), back.cmds());
        assertFalse(back.model().jobs().away());
        assertTrue(back.model().jobs().missed().isEmpty());
    }

    @Test
    void whatTheUserDoesIsHandledWhileAway() {
        Model away = Update.update(loaded(), new Msg.TerminalFocused(false)).model();

        assertEquals(
                Update.update(loaded(), new Msg.Clock()).model().screen(),
                Update.update(away, new Msg.Clock()).model().screen());
        assertTrue(Update.update(away, new Msg.Quit()).quit());
    }

    @Test
    void backWithoutAnyTimerMeanwhileReadsNothing() {
        Model away = Update.update(loaded(), new Msg.TerminalFocused(false)).model();

        assertEquals(
                List.empty(), Update.update(away, new Msg.TerminalFocused(true)).cmds());
    }

    private static final Instant AWAY_AT = Instant.parse("2026-10-31T20:00:00Z");

    @Test
    void awayATickIsHandledOnceItWaited() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();
        away = Update.update(away, new Msg.Tick()).model();
        away = Update.update(at(away, Msg.Tick.WHILE_AWAY.minusSeconds(1)), new Msg.Tick())
                .model();

        Next ticked = Update.update(at(away, Msg.Tick.WHILE_AWAY), new Msg.Tick());

        assertEquals(
                Update.update(at(loaded().withNow(AWAY_AT), Msg.Tick.WHILE_AWAY), new Msg.Tick())
                        .cmds(),
                ticked.cmds());
        assertFalse(ticked.cmds().isEmpty());
        assertTrue(ticked.model().jobs().away());
        assertFalse(ticked.model().jobs().missed().contains(new Msg.Tick()));
    }

    @Test
    void awayATickWaitsAnewOnceHandled() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();
        away = Update.update(away, new Msg.Tick()).model();
        away = Update.update(at(away, Msg.Tick.WHILE_AWAY), new Msg.Tick()).model();

        Next ticked = Update.update(at(away, Msg.Tick.WHILE_AWAY.multipliedBy(2).minusSeconds(1)), new Msg.Tick());

        assertEquals(List.empty(), ticked.cmds());
        assertTrue(ticked.model().jobs().missed().contains(new Msg.Tick()));
    }

    @Test
    void awayAFetchWaitsLongerThanATick() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();
        away = Update.update(away, new FetchMsg.Tick()).model();

        Next fetched = Update.update(at(away, Msg.Tick.WHILE_AWAY), new FetchMsg.Tick());

        assertEquals(List.empty(), fetched.cmds());
        assertTrue(FetchMsg.Tick.WHILE_AWAY.compareTo(Msg.Tick.WHILE_AWAY) > 0);
    }

    @Test
    void losingTheFocusAgainDoesNotStartTheWaitsAnew() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();
        away = Update.update(away, new Msg.Tick()).model();
        away = Update.update(at(away, Duration.ofSeconds(10)), new Msg.TerminalFocused(false))
                .model();

        assertFalse(Update.update(at(away, Msg.Tick.WHILE_AWAY), new Msg.Tick())
                .cmds()
                .isEmpty());
    }

    @Test
    void awayTheClockIsDrawnEveryFiveSeconds() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();

        assertEquals(away.drawn(), at(away, Model.AWAY_CLOCK.minusMillis(1)).drawn());
        assertNotEquals(away.drawn(), at(away, Model.AWAY_CLOCK).drawn());
        Model focused = loaded().withNow(AWAY_AT);
        assertNotEquals(focused.drawn(), at(focused, Duration.ofSeconds(1)).drawn());
    }

    @Test
    void awayWhatTheTimersKeepIsNotDrawn() {
        Model away = Update.update(loaded().withNow(AWAY_AT), new Msg.TerminalFocused(false))
                .model();

        assertEquals(away.drawn(), Update.update(away, new Msg.Tick()).model().drawn());
    }

    private static Model at(Model model, Duration later) {
        return model.withNow(AWAY_AT.plus(later));
    }
}
