package net.vanfleteren.jazygit.feature.branch;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup with the options on the upstream of the checked out branch.
 */
class UpstreamDialogTest {

    private static final Branch TRACKING = new Branch(
            new Branch.Name("feature"),
            true,
            new Sha("ffff"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 0, 0)));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<UpstreamPopup> dialog = new ChoiceDialog<>(BranchDialogs.UPSTREAM, dispatched::add);

    @Test
    void namesTheBranchAndItsUpstream() throws Exception {
        String screen = render(new UpstreamPopup(Option.of(TRACKING)));

        assertTrue(screen.contains("Upstream options for feature"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto origin/feature"), screen);
        assertTrue(screen.contains("(c) Cancel"), screen);
    }

    @Test
    void withoutAnUpstreamTheOptionIsStillOffered() throws Exception {
        String screen = render(new UpstreamPopup(Option.of(new Branch(new Branch.Name("main"), true, new Sha("a")))));

        assertTrue(screen.contains("Upstream options for main"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto upstream"), screen);
    }

    @Test
    void withADetachedHeadTheTitleNamesNoBranch() throws Exception {
        String screen = render(new UpstreamPopup(Option.none()));

        assertTrue(screen.contains("Upstream options"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto upstream"), screen);
    }

    @Test
    void gChoosesTheReset() throws Exception {
        Model open = TestModels.loaded().openPopup(new UpstreamPopup(Option.of(TRACKING)));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, BranchDialogs.UPSTREAM.id());
            RenderedText.of(runner, () -> view(open));
            SettledPilot.of(runner).press('g');
            SettledPilot.of(runner).pause();
        }
        assertEquals(List.of(new UpstreamMsg.ResetChosen()), List.ofAll(dispatched));
    }

    private String render(UpstreamPopup popup) throws Exception {
        Model open = TestModels.loaded().openPopup(popup);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            return RenderedText.ofLarge(runner, () -> view(open));
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
