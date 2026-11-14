package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadStatus;
import org.junit.jupiter.api.Test;

/**
 * The user can turn off reading the repository and fetching from the remotes every so often; what is read then is
 * read at the start, after the app's own commands, when the focus comes back, and when asked for (ADR 125).
 */
class TimerSwitchesTest {

    private static final Instant AWAY_AT = Instant.parse("2026-11-14T20:00:00Z");

    private static Model with(Model model, Settings settings) {
        return model.mapPreferences(preferences -> preferences.withSettings(settings));
    }

    private static Model autoRefreshOff() {
        return with(loaded().withNow(AWAY_AT), Settings.DEFAULT.withAutoRefresh(false));
    }

    private static Model autoFetchOff() {
        return with(loaded().withNow(AWAY_AT), Settings.DEFAULT.withAutoFetch(false));
    }

    private static Model away(Model model) {
        return Update.update(model, new TerminalMsg.Focused(false)).model();
    }

    @Test
    void withAutoRefreshOffAFocusedTickReadsNothing() {
        Next ticked = Update.update(autoRefreshOff(), new TimerMsg.Tick());

        assertEquals(List.empty(), ticked.cmds());
        assertEquals(autoRefreshOff().jobs().ticks(), ticked.model().jobs().ticks());
    }

    @Test
    void withAutoRefreshOffATickWhileAwayIsKeptButNotReadOnItsWhile() {
        Model away = Update.update(away(autoRefreshOff()), new TimerMsg.Tick()).model();

        Next later = Update.update(
                away.withNow(AWAY_AT.plus(TimerMsg.Tick.WHILE_AWAY.multipliedBy(2))), new TimerMsg.Tick());

        assertEquals(List.empty(), later.cmds());
        assertTrue(later.model().jobs().missed().contains(new TimerMsg.Tick()));
    }

    @Test
    void withAutoRefreshOffATickKeptWhileAwayReadsTheStatusOnceTheFocusComesBack() {
        Model away = Update.update(away(autoRefreshOff()), new TimerMsg.Tick()).model();

        Next back = Update.update(away, new TerminalMsg.Focused(true));

        assertTrue(back.cmds().contains(new LoadStatus()));
        assertTrue(back.model().jobs().missed().isEmpty());
    }

    @Test
    void withAutoRefreshOffTheAppsOwnCommandsStillReadTheRepository() {
        assertTrue(Update.refresh(autoRefreshOff()).cmds().contains(new LoadStatus()));
    }

    @Test
    void turnedBackOnTheNextTickReads() {
        Model on = with(autoRefreshOff(), Settings.DEFAULT);

        assertTrue(Update.update(on, new TimerMsg.Tick()).cmds().contains(new LoadStatus()));
    }

    @Test
    void withAutoFetchOffATickFetchesNothingFocusedOrAway() {
        assertEquals(
                List.empty(), Update.update(autoFetchOff(), new FetchMsg.Tick()).cmds());

        Next away = Update.update(away(autoFetchOff()), new FetchMsg.Tick());
        assertEquals(List.empty(), away.cmds());
        assertTrue(away.model().jobs().missed().isEmpty());
        assertEquals(
                List.empty(),
                Update.update(away.model(), new TerminalMsg.Focused(true)).cmds());
    }

    @Test
    void withAutoFetchOffTheRepositoryIsStillReadOnATick() {
        assertTrue(Update.update(autoFetchOff(), new TimerMsg.Tick()).cmds().contains(new LoadStatus()));
    }

    @Test
    void aFetchKeptWhileAwayIsDroppedOnceAutoFetchIsTurnedOffBeforeTheFocusComesBack() {
        Model away = Update.update(away(loaded().withNow(AWAY_AT)), new FetchMsg.Tick())
                .model();
        assertTrue(away.jobs().missed().contains(new FetchMsg.Tick()));

        Next back = Update.update(with(away, Settings.DEFAULT.withAutoFetch(false)), new TerminalMsg.Focused(true));

        assertFalse(back.cmds().exists(FetchCmd.class::isInstance));
        assertTrue(back.model().jobs().missed().isEmpty());
    }

    @Test
    void withAutoFetchOffAFetchTheUserAsksForStillRuns() {
        assertEquals(
                List.of(new FetchCmd.ListRemotes()),
                Update.update(autoFetchOff(), new FetchMsg.Requested()).cmds());
    }
}
