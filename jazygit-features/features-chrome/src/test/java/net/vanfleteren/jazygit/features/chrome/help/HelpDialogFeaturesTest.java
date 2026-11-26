package net.vanfleteren.jazygit.features.chrome.help;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.CursorFeedback;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The help of a key that two features bind alike, as two forges do (ADR 175): it lists the key once, as the first of
 * them that is on has it.
 */
class HelpDialogFeaturesTest {

    private final CursorFeedback cursor = new CursorFeedback(msg -> {});

    // Message keys of the kernel's bundle, so that the two read differently in the help.
    private final HelpDialog dialog = new HelpDialog(
            cursor,
            Keymap.withHelp(HelpMsg.Requested::new)
                    .owned(
                            new FeatureId("f"),
                            keys -> keys.add(Scopes.BRANCHES, Binding.always("Z", "key.space", Probe::new))
                                    .global(Keymap.Global.Dispatching.ofChar('Y', "key.space", Probe::new)))
                    .owned(
                            new FeatureId("g"),
                            keys -> keys.add(Scopes.BRANCHES, Binding.always("Z", "panel.files.title", Probe::new))
                                    .global(Keymap.Global.Dispatching.ofChar('Y', "panel.files.title", Probe::new))));

    @Test
    void aKeyBothFeaturesBindIsListedOnceAsTheFirstThatIsOnHasIt() throws Exception {
        String both = screen(TestModels.loaded());
        assertEquals(1, occurrences(both, " Z "), both);
        assertTrue(both.contains("Z space"), both);
        assertEquals(1, occurrences(both, " Y "), both);
        assertTrue(both.contains("Y space"), both);

        String onlyG = screen(FeatureUpdate.off(TestModels.loaded(), new FeatureId("f")));
        assertEquals(1, occurrences(onlyG, " Z "), onlyG);
        assertTrue(onlyG.contains("Z Files"), onlyG);
        assertEquals(1, occurrences(onlyG, " Y "), onlyG);
        assertTrue(onlyG.contains("Y Files"), onlyG);
    }

    private static int occurrences(String screen, String text) {
        return List.of(screen.split("\n")).count(line -> line.contains(text));
    }

    private String screen(Model model) throws Exception {
        Model open = Update.update(model, new HelpMsg.Requested(Scopes.BRANCHES.id(), List.empty()))
                .model();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            return RenderedText.ofSize(runner, () -> view(open), 100, 30);
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }

    private record Probe() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
