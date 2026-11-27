package net.vanfleteren.jazygit.kernel.state.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import org.junit.jupiter.api.Test;

/**
 * Switching screens, whichever screen it is (ADR 168).
 */
class ScreenUpdateTest {

    // A screen a feature brings, which the kernel knows nothing of.
    private static final PaneId TOP = new PaneId("top");
    private static final PaneId BOTTOM = new PaneId("bottom");
    private static final Screen OTHER = new Screen(
            "other",
            List.of(TOP, BOTTOM),
            Settings.leftWidth("ui.other-width"),
            Settings.leftHeights("ui.other-heights", List.of(50)));

    // Tells which screens were shown, by marking the model's repository name.
    private static final Reactions TOLD = new Reactions(List.of((model, event) -> Next.of(
            event instanceof KernelEvent.ScreenShown(Screen screen)
                    ? model.withRepositoryName(model.repositoryName() + " " + screen.id())
                    : model)));

    private static Model loaded() {
        return TestModels.loaded().withRepositoryName("shown:").withReactions(TOLD);
    }

    @Test
    void showingAScreenShowsItAndTellsTheFeatures() {
        Model shown = Update.update(loaded(), new ScreenMsg.Shown(OTHER)).model();

        assertEquals(OTHER, shown.screen());
        assertEquals("shown: other", shown.repositoryName());
    }

    @Test
    void showingTheScreenShownDoesNothing() {
        Next again = Update.update(loaded(), new ScreenMsg.Shown(Screen.REPOSITORY));

        assertEquals(Next.of(loaded()), again);
    }

    @Test
    void itsShortcutShowsAScreenOrTheRepositoryWhenItIsShown() {
        Model shown = Update.update(loaded(), new ScreenMsg.Toggled(OTHER)).model();
        Model back = Update.update(shown, new ScreenMsg.Toggled(OTHER)).model();

        assertEquals(OTHER, shown.screen());
        assertEquals(Screen.REPOSITORY, back.screen());
        assertEquals("shown: other repository", back.repositoryName());
    }

    @Test
    void aScreenStartsWithItsFirstPaneAndGetsItsFocusBack() {
        Model shown = Update.update(loaded(), new ScreenMsg.Shown(OTHER)).model();
        Model bottom = Update.update(shown, new ViewMsg.Focused(BOTTOM)).model();
        Model again = Update.update(
                        Update.update(bottom, new ScreenMsg.Shown(Screen.REPOSITORY))
                                .model(),
                        new ScreenMsg.Shown(OTHER))
                .model();

        assertEquals(TOP, OTHER.first());
        assertEquals(Option.none(), shown.shownPane());
        assertEquals(Option.of(BOTTOM), again.shownPane());
        assertEquals(Option.of(BOTTOM), again.paneInUse(OTHER));
    }

    @Test
    void aScreenOpensOnThePaneItSaysIfItIsOneOfItsOwn() {
        assertEquals(BOTTOM, OTHER.opensOn(BOTTOM).first());
        assertEquals(TOP, OTHER.opensOn(new PaneId("elsewhere")).first());
    }

    @Test
    void theLayoutOfAScreenIsKeptUnderItsOwnSettings() {
        Preferences wide = Preferences.DEFAULT.withSettings(Preferences.DEFAULT.keepingWidth(OTHER, 50));

        assertEquals(50, wide.keptWidth(OTHER));
        assertEquals(Settings.DEFAULT_WIDTH, wide.keptWidth(Screen.REPOSITORY));
        assertEquals(List.of(50), wide.keptHeights(OTHER));
    }
}
