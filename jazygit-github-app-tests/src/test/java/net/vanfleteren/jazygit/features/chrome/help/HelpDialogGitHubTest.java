package net.vanfleteren.jazygit.features.chrome.help;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.CursorFeedback;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import org.junit.jupiter.api.Test;

/**
 * The tests of HelpDialogTest with GitHub in the app (ADR 171).
 */
class HelpDialogGitHubTest {

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
    void theDescriptionsOfTheLegendLineUpWhateverTheWidthOfTheSymbols() throws Exception {
        List<String> lines = List.of(screen(Scopes.BRANCHES.id()).split("\n"));
        // The symbols in front of the descriptions differ in width: #42 takes three columns, those of the checks
        // seven and those of the review five.
        List<Integer> columns = List.of("Pull request (dim: draft)", "CI passed", "Approved / changes")
                .map(description -> {
                    String line = lines.find(text -> text.contains(description)).get();
                    return TextWidth.of(line.substring(0, line.indexOf(description)));
                });
        assertEquals(1, columns.distinct().size(), columns.toString());
    }

    @Test
    void theLegendSaysWhatGitsSymbolsMeanThenWhatTheFeaturesAdd() throws Exception {
        List<String> lines = List.of(screen(Scopes.BRANCHES.id()).split("\n"));
        // features-chrome keeps git's, and GitHub adds what its badges mean (ADR 168).
        List<Integer> rows = List.of(
                        "Its upstream was deleted",
                        "Checked out in another worktree",
                        "Pull request (dim: draft)",
                        "Where its pull request sits in its stack")
                .map(description -> lines.indexWhere(line -> line.contains(description)));
        assertEquals(rows.sorted(), rows, rows.toString());
        assertFalse(rows.contains(-1), rows.toString());
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
            // The keys of the branches, their legend and the global keys take more than the 44 lines of ofTall.
            return RenderedText.ofSize(runner, () -> view(open), 100, 54);
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }
}
