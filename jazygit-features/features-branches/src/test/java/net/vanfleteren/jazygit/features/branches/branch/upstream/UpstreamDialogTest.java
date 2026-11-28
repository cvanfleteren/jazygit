package net.vanfleteren.jazygit.features.branches.branch.upstream;

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
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup with the options on the upstream: the divergence of the highlighted branch, and the reset of the checked
 * out one.
 */
class UpstreamDialogTest {

    private static final Branch TRACKING = Fixtures.branch(
            new BranchName("feature"),
            true,
            new Sha("ffff"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 0, 0)));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<UpstreamPopup> dialog = new ChoiceDialog<>(UpstreamDialogs.UPSTREAM, dispatched::add);

    @Test
    void namesTheBranchItsUpstreamAndTheBase() throws Exception {
        String screen = render(new UpstreamPopup(
                Option.of(TRACKING),
                Option.of(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main")))),
                Option.of(TRACKING)));

        assertTrue(screen.contains("Upstream options for feature"), screen);
        assertTrue(screen.contains("(v) view divergence from origin/feature"), screen);
        assertTrue(screen.contains("(b) view divergence from base branch origin/main"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto origin/feature"), screen);
    }

    @Test
    void withoutAnUpstreamOrBaseTheOptionsAreStillOffered() throws Exception {
        Branch main = Fixtures.branch(new BranchName("main"), true, new Sha("a"));
        String screen = render(new UpstreamPopup(Option.of(main), Option.none(), Option.of(main)));

        assertTrue(screen.contains("Upstream options for main"), screen);
        assertTrue(screen.contains("(v) view divergence from upstream"), screen);
        assertTrue(screen.contains("(b) view divergence from base branch"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto upstream"), screen);
    }

    @Test
    void withoutBranchesTheTitleNamesNone() throws Exception {
        String screen = render(new UpstreamPopup(Option.none(), Option.none(), Option.none()));

        assertTrue(screen.contains("Upstream options"), screen);
        assertTrue(screen.contains("(g) reset checked out branch onto upstream"), screen);
    }

    @Test
    void theKeysChooseTheOptions() throws Exception {
        Model open = TestModels.loaded()
                .openPopup(new UpstreamPopup(Option.of(TRACKING), Option.none(), Option.of(TRACKING)));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, UpstreamDialogs.UPSTREAM.id());
            RenderedText.of(runner, () -> view(open));
            SettledPilot.of(runner).press('v');
            SettledPilot.of(runner).press('b');
            SettledPilot.of(runner).press('g');
            SettledPilot.of(runner).pause();
        }
        assertEquals(
                List.of(
                        new UpstreamMsg.UpstreamDivergenceChosen(),
                        new UpstreamMsg.BaseDivergenceChosen(),
                        new UpstreamMsg.ResetChosen()),
                List.ofAll(dispatched));
    }

    @Test
    void theKeysSetAndUnsetTheUpstreamOfABranchThatHasOne() throws Exception {
        UpstreamPopup popup = new UpstreamPopup(Option.of(TRACKING), Option.none(), Option.none());
        assertTrue(render(popup).contains("(s) set upstream"));
        assertTrue(render(popup).contains("(u) unset upstream"));

        assertEquals(
                List.of(new UpstreamMsg.SetChosen(), new UpstreamMsg.UnsetChosen()),
                pressed(withARemoteBranch(), popup, 's', 'u'));
    }

    @Test
    void withoutAnyRemoteBranchSettingTheUpstreamIsOfferedButNotChosen() throws Exception {
        UpstreamPopup popup = new UpstreamPopup(Option.of(TRACKING), Option.none(), Option.none());

        assertTrue(render(popup).contains("(s) set upstream"));
        assertEquals(List.of(), pressed(TestModels.loaded(), popup, 's'));
    }

    @Test
    void aBranchWithoutUpstreamStillOffersToUnsetItButDoesNotChooseIt() throws Exception {
        Branch main = Fixtures.branch(new BranchName("main"), true, new Sha("a"));
        UpstreamPopup popup = new UpstreamPopup(Option.of(main), Option.none(), Option.none());

        assertTrue(render(popup).contains("(u) unset upstream"));
        assertEquals(List.of(), pressed(popup, 'u'));
    }

    private static Model withARemoteBranch() {
        return TestModels.remoteBranchesRead(
                        TestModels.loaded(),
                        List.of(new RemoteRef(
                                new RemoteBranch("origin", new BranchName("main")), new Sha("a0a0"), Instant.EPOCH)))
                .model();
    }

    private List<Msg> pressed(UpstreamPopup popup, char... keys) throws Exception {
        return pressed(TestModels.loaded(), popup, keys);
    }

    private List<Msg> pressed(Model model, UpstreamPopup popup, char... keys) throws Exception {
        dispatched.clear();
        Model open = model.openPopup(popup);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, UpstreamDialogs.UPSTREAM.id());
            RenderedText.of(runner, () -> view(open));
            for (char key : keys) {
                SettledPilot.of(runner).press(key);
            }
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
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
