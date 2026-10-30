package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * While the terminal does not have the focus, the timers read nothing; once it has it again, what they asked for
 * meanwhile is read once (ADR 58).
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
}
