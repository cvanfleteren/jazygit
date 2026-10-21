package net.vanfleteren.jazygit.feature.layout;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class LayoutUpdateTest {

    private final Model model = TestModels.loaded();

    @Test
    void aStepWidensTheLeftColumnOfTheScreenShownAndKeepsIt() {
        Next next = Update.update(model, new LayoutMsg.Stepped(5));

        Settings wider = Settings.DEFAULT.withRepositoryWidth(35);
        assertEquals(wider, next.model().preferences().settings());
        assertEquals(35, next.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(30, next.model().preferences().leftWidth(Screen.PULL_REQUESTS));
        assertEquals(List.of(new Cmd.SaveSettings(Settings.DEFAULT, wider)), next.cmds());
    }

    @Test
    void eachScreenHasItsOwnWidth() {
        Next next = Update.update(model.withScreen(Screen.PULL_REQUESTS), new LayoutMsg.Stepped(-5));

        assertEquals(
                Settings.DEFAULT.withPullRequestsWidth(25),
                next.model().preferences().settings());
    }

    @Test
    void theWidthStaysWithinItsRangeAndAStepPastItKeepsNothing() {
        Model narrowest = model.mapPreferences(
                preferences -> preferences.withSettings(Settings.DEFAULT.withRepositoryWidth(Settings.MIN_WIDTH)));

        Next next = Update.update(narrowest, new LayoutMsg.Stepped(-5));

        assertEquals(narrowest, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theKeysDoNothingWhileAPopupIsOpen() {
        Model open = model.openPopup(new ThemePopup(ThemeName.DARK, Option.none()));

        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.Stepped(5)));
        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.Resized(Screen.REPOSITORY, 50)));
    }

    @Test
    void aDraggedWidthShowsAndIsOnlyKeptWhenLetGo() {
        Next dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 90));

        assertEquals(Settings.MAX_WIDTH, dragged.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(Settings.DEFAULT, dragged.model().preferences().settings());
        assertEquals(List.of(), dragged.cmds());

        Next settled = Update.update(dragged.model(), new LayoutMsg.Settled(Screen.REPOSITORY));

        Settings kept = Settings.DEFAULT.withRepositoryWidth(Settings.MAX_WIDTH);
        assertEquals(kept, settled.model().preferences().settings());
        assertEquals(Option.none(), settled.model().preferences().dragged());
        assertEquals(List.of(new Cmd.SaveSettings(Settings.DEFAULT, kept)), settled.cmds());
    }

    @Test
    void lettingGoWhereItStartedKeepsNothing() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 30))
                .model();

        Next settled = Update.update(dragged, new LayoutMsg.Settled(Screen.REPOSITORY));

        assertEquals(model, settled.model());
        assertEquals(List.of(), settled.cmds());
    }

    @Test
    void aStepEndsADragAndALateReleaseKeepsTheStep() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 50))
                .model();

        Next stepped = Update.update(dragged, new LayoutMsg.Stepped(5));

        assertEquals(55, stepped.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(Option.none(), stepped.model().preferences().dragged());
        Next released = Update.update(stepped.model(), new LayoutMsg.Settled(Screen.REPOSITORY));
        assertEquals(Next.of(stepped.model()), released);
    }

    @Test
    void lettingGoOfAnotherScreenOrOfNoDragDoesNothing() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 50))
                .model();

        assertEquals(Next.of(dragged), Update.update(dragged, new LayoutMsg.Settled(Screen.PULL_REQUESTS)));
        assertEquals(Next.of(model), Update.update(model, new LayoutMsg.Settled(Screen.REPOSITORY)));
    }
}
