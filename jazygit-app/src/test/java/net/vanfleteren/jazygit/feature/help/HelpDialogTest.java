package net.vanfleteren.jazygit.feature.help;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.push.PushMsg;
import net.vanfleteren.jazygit.feature.repos.RecentReposMsg;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.TextWidth;
import org.junit.jupiter.api.Test;

/**
 * The popup listing the keys of a panel: Enter or the key itself runs one, Escape or ? closes it.
 */
class HelpDialogTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final HelpDialog dialog = new HelpDialog(cursor);

    private static Model open(String topic) {
        return open(topic, Option.none());
    }

    /**
     * The help of {@code topic}, opened with {@code selection} selected in the panel.
     */
    private static <S> Model open(Keymap.Scope<S> scope, Option<S> selection) {
        return open(scope.id(), Option.of(Keymap.standard().actions(scope, selection)));
    }

    private static Model open(String topic, Option<List<Option<Msg>>> actions) {
        return Update.update(TestModels.loaded(), new HelpMsg.Requested(topic, actions.getOrElse(List.empty())))
                .model();
    }

    private static Model onFeature() {
        return open(Scopes.BRANCHES, Option.of(FEATURE));
    }

    @Test
    void listsTheKeysOfTheFilesPanel() throws Exception {
        String screen = screen(Scopes.FILES.id());
        assertTrue(screen.contains("Keybindings: Files"), screen);
        assertTrue(screen.contains("       space Stage/unstage"), screen);
        assertTrue(screen.contains("           c Commit"), screen);
        assertTrue(screen.contains("           A Amend last commit"), screen);
    }

    @Test
    void listsTheKeysOfTheBranchesPanel() throws Exception {
        String screen = screen(Scopes.BRANCHES.id());
        assertTrue(screen.contains("       space Checkout branch"), screen);
        assertTrue(screen.contains("           n New branch"), screen);
        assertTrue(screen.contains("           d Delete branch"), screen);
    }

    @Test
    void theDescriptionsOfTheLegendLineUpWhateverTheWidthOfTheSymbols() throws Exception {
        List<String> lines = List.of(screen(Scopes.BRANCHES.id()).split("\n"));
        // #42 takes a column a character, the emoji of the checks and the review two.
        List<Integer> columns = List.of("Pull request (dim: draft)", "CI passed", "Approved / changes")
                .map(description -> {
                    String line = lines.find(text -> text.contains(description)).get();
                    return TextWidth.of(line.substring(0, line.indexOf(description)));
                });
        assertEquals(1, columns.distinct().size(), columns.toString());
    }

    @Test
    void listsTheKeysOfTheCommitsPanel() throws Exception {
        assertTrue(screen(Scopes.COMMITS.id()).contains("           r Reword commit"));
    }

    @Test
    void everyPanelListsTheGlobalKeysAfterItsOwn() throws Exception {
        for (String scope : List.of(Scopes.FILES.id(), Scopes.BRANCHES.id(), Scopes.COMMITS.id(), Scopes.STASH.id())) {
            String screen = screen(scope);
            assertTrue(screen.contains("Global"), screen);
            assertTrue(screen.contains("      ctrl+s Show the settings"), screen);
        }
    }

    @Test
    void escapeAndQuestionMarkClose() throws Exception {
        assertEquals(
                List.of(new Msg.PopupDismissed(HelpPopup.class)),
                press(r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
        assertEquals(
                List.of(new Msg.PopupDismissed(HelpPopup.class)),
                press(r -> SettledPilot.of(r).press('?')));
    }

    @Test
    void enterRunsTheHighlightedKeyStartingAtTheFirst() throws Exception {
        assertEquals(
                List.of(new HelpMsg.Chosen(new CheckoutMsg.Requested(FEATURE))),
                press(onFeature(), r -> SettledPilot.of(r).press(KeyCode.ENTER)));
        assertEquals(List.of(new HelpMsg.Chosen(new PushMsg.Requested(FEATURE))), press(onFeature(), r -> {
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
    }

    @Test
    void theArrowsSkipTheHeadingsAndStopAtTheEnds() throws Exception {
        int panelKeys = Keymap.standard().bindings(Scopes.BRANCHES.id()).size();
        // Past the panel keys, the legend and the heading, to the second global key, after ?.
        assertEquals(List.of(new HelpMsg.Chosen(new RecentReposMsg.Requested())), press(onFeature(), r -> {
            for (int i = 0; i < panelKeys + 1; i++) {
                SettledPilot.of(r).press(KeyCode.DOWN);
            }
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
        assertEquals(List.of(new HelpMsg.Chosen(new CheckoutMsg.Requested(FEATURE))), press(onFeature(), r -> {
            SettledPilot.of(r).press(KeyCode.UP);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
    }

    @Test
    void pressingAKeyRunsIt() throws Exception {
        assertEquals(
                List.of(new HelpMsg.Chosen(new DeleteBranchMsg.Requested(FEATURE))),
                press(onFeature(), r -> SettledPilot.of(r).press('d')));
        assertEquals(
                List.of(new HelpMsg.Chosen(new SettingsPanelMsg.Requested())),
                press(onFeature(), r -> SettledPilot.of(r).press('s', KeyModifiers.CTRL)));
    }

    @Test
    void aGlobalKeyWithCtrlIsNotThePanelKeyWithout() throws Exception {
        // The branches panel has r, for rebase.
        assertEquals(
                List.of(new HelpMsg.Chosen(new RecentReposMsg.Requested())),
                press(onFeature(), r -> SettledPilot.of(r).press('r', KeyModifiers.CTRL)));
    }

    @Test
    void theGlobalKeysRunToo() throws Exception {
        int panelKeys = Keymap.standard().bindings(Scopes.BRANCHES.id()).size();
        assertEquals(List.of(new HelpMsg.Chosen(new Msg.PopupDismissed(HelpPopup.class))), press(onFeature(), r -> {
            for (int i = 0; i < panelKeys; i++) {
                SettledPilot.of(r).press(KeyCode.DOWN);
            }
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
        // Quitting goes to the app as is.
        assertEquals(
                List.of(new Msg.Quit()),
                press(onFeature(), r -> SettledPilot.of(r).press('q')));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithout() throws Exception {
        Model none = open(Scopes.BRANCHES, Option.none());
        assertEquals(List.empty(), press(none, r -> SettledPilot.of(r).press(KeyCode.ENTER)));
        assertEquals(List.empty(), press(none, r -> SettledPilot.of(r).press('d')));
    }

    @Test
    void theDetailsOfTheHighlightedKeyShowUnderThePopup() throws Exception {
        Model open = onFeature();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, HelpDialog.ID);
            String checkout = RenderedText.ofTall(runner, () -> view(open));
            assertTrue(checkout.contains("Switches to the selected branch."), checkout);

            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).pause();
            String push = RenderedText.ofTall(runner, () -> view(open));
            assertFalse(push.contains("Switches to the selected branch."), push);
            // The popup stays where it was.
            assertEquals(
                    checkout.lines()
                            .filter(line -> line.contains("Keybindings"))
                            .toList(),
                    push.lines().filter(line -> line.contains("Keybindings")).toList());
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(cursor.on(TestModels.loaded())).isEmpty());
    }

    private List<Msg> press(java.util.function.Consumer<ToolkitTestRunner> keys) throws Exception {
        return press(open(Scopes.FILES.id()), keys);
    }

    private List<Msg> press(Model open, java.util.function.Consumer<ToolkitTestRunner> keys) throws Exception {
        dispatched.clear();
        // Closed in between, which the model forgets where the user was in, so it starts at the top again.
        cursor.reset();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, HelpDialog.ID);
            RenderedText.of(runner, () -> view(open));
            keys.accept(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private String screen(String topic) throws Exception {
        Model open = open(topic);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            return RenderedText.ofTall(runner, () -> view(open));
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }
}
