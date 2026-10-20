package net.vanfleteren.jazygit.feature.commit.reset;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popups asking how to reset to a commit, and to confirm a hard reset.
 */
class ResetDialogTest {

    private static final Commit TARGET =
            new Commit(new Sha("1111111111"), "Ada", "ada@example.com", Instant.EPOCH, "Target", "");

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<ResetPopup> dialog = new ChoiceDialog<>(ResetDialogs.RESET, dispatched::add);
    private final Model open = Update.update(
                    Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(TARGET)))
                            .model(),
                    new ResetMsg.Requested(TARGET.sha()))
            .model();

    private static String render(java.util.function.Supplier<Element> view) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view::get)) {
            return RenderedText.ofLarge(runner, view::get);
        }
    }

    @Test
    void listsTheThreeResets() throws Exception {
        String screen =
                render(() -> stack(text("background"), dialog.render(open).get()));

        assertTrue(screen.contains("Reset to " + TARGET.sha().shortSha()), screen);
        assertTrue(screen.contains("(m) Mixed reset: keep changes, unstaged"), screen);
        assertTrue(screen.contains("(s) Soft reset: keep changes, staged"), screen);
        assertTrue(screen.contains("(h) Hard reset: discard all changes"), screen);
    }

    @Test
    void eachLetterChoosesItsReset() throws Exception {
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.MIXED)), pressing('m'));
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.SOFT)), pressing('s'));
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.HARD)), pressing('h'));
    }

    @Test
    void theHardResetConfirmationSaysHowManyFilesLoseTheirChanges() throws Exception {
        assertTrue(confirmation(Option.of(1)).contains("Discard the changes to 1 file?"));
        assertTrue(confirmation(Option.of(3)).contains("Discard the changes to 3 files?"));
        assertTrue(confirmation(Option.none()).contains("Discard all uncommitted changes?"));
        assertTrue(confirmation(Option.none())
                .contains("Hard reset to " + TARGET.sha().shortSha()));
    }

    @Test
    void aResetOntoTheUpstreamSaysHowManyLocalCommitsItDrops() throws Exception {
        RemoteBranch upstream = new RemoteBranch("origin", new Branch.Name("main"));

        String one = choice(new ResetPopup(new ResetTarget.ToUpstream(upstream), 1));
        assertTrue(one.contains("Reset to origin/main (drops 1 local commit)"), one);
        assertTrue(choice(new ResetPopup(new ResetTarget.ToUpstream(upstream), 3))
                .contains("Reset to origin/main (drops 3 local commits)"));
        String none = choice(new ResetPopup(new ResetTarget.ToUpstream(upstream), 0));
        assertTrue(none.contains("Reset to origin/main"), none);
        assertFalse(none.contains("drops"), none);
    }

    private String choice(ResetPopup popup) throws Exception {
        Model asking = TestModels.loaded().openPopup(popup);
        return render(() -> stack(text("background"), dialog.render(asking).get()));
    }

    private String confirmation(Option<Integer> files) throws Exception {
        ConfirmDialog<HardResetPopup> confirm = new ConfirmDialog<>(ResetDialogs.HARD_RESET, dispatched::add);
        Model asking = TestModels.loaded().openPopup(new HardResetPopup(new ResetTarget.ToCommit(TARGET.sha()), files));
        return render(() -> stack(text("background"), confirm.render(asking).get()));
    }

    private List<Msg> pressing(char key) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, ResetDialogs.RESET.id());
            RenderedText.of(runner, this::view);
            SettledPilot.of(runner).press(key);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
