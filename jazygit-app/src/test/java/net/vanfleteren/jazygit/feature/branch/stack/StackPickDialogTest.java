package net.vanfleteren.jazygit.feature.branch.stack;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.popup.ListCursor;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

/**
 * The popup that chooses the bottom and the top of a stack to make, as ADR 73 draws it.
 */
class StackPickDialogTest {

    private static BranchName name(String name) {
        return new BranchName(name);
    }

    private static Branch branch(String name) {
        return Fixtures.branch(name(name), false, new Sha("aaaa"));
    }

    private static PullRequest pr(int number, String head, String base) {
        return new PullRequest(
                number,
                new GhBranch(head),
                new GhBranch(base),
                false,
                Checks.NONE,
                Review.NONE,
                Option.none(),
                Option.none());
    }

    private static final Map<BranchName, PullRequest> OPEN = HashMap.of(
            name("feat/a"), pr(96, "feat/a", "develop"),
            name("feat/b"), pr(97, "feat/b", "feat/a"),
            name("feat/c"), pr(98, "feat/c", "feat/b"));

    // develop ← feat/a ← feat/b ← feat/c ← feat/d, and feat/b ← feat/e: the example of ADR 73, opened on feat/b.
    private static final StackChoice CHOICE = StackChoice.of(
            branch("feat/b"),
            List.of("develop", "feat/a", "feat/b", "feat/c", "feat/d", "feat/e").map(StackPickDialogTest::branch),
            HashMap.of(
                    name("feat/a"), name("develop"),
                    name("feat/b"), name("feat/a"),
                    name("feat/c"), name("feat/b"),
                    name("feat/d"), name("feat/c"),
                    name("feat/e"), name("feat/b")),
            n -> n.equals(name("develop")),
            b -> OPEN.get(b.name()));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final StackPickDialog dialog = new StackPickDialog(dispatched::add);

    @Test
    void theLineTheBranchAndTheTreeAboveItShowWithTheirPullRequests() throws Exception {
        String screen = render(new StackPopups.Pick(CHOICE, Option.none(), Option.none()));

        assertTrue(screen.contains("Make a stack → develop"), screen);
        assertTrue(screen.contains("┗ feat/a  #96"), screen);
        assertTrue(screen.contains("┏ feat/b  #97  ← this branch"), screen);
        assertTrue(screen.contains("    feat/c  #98"), screen);
        assertTrue(screen.contains("      feat/d  no pull request"), screen);
        assertTrue(screen.contains("    feat/e  no pull request"), screen);
        assertTrue(screen.contains("(b) bottom"), screen);
    }

    @Test
    void whyTheChosenBranchesMakeNoStackShowsUnderThem() throws Exception {
        String screen = render(new StackPopups.Pick(CHOICE.withTop(3).get(), Option.none(), Option.none()));

        assertTrue(screen.contains("┃   feat/c  #98"), screen);
        assertTrue(screen.contains("┏     feat/d  no pull request"), screen);
        assertTrue(screen.contains("Without an open pull request: feat/d"), screen);
    }

    @Test
    void aBranchBehindTheOneUnderItSaysByHowMuch() throws Exception {
        Model lagging = TestModels.loaded()
                .mapRepo(repo -> repo.withParents(HashMap.of(name("feat/c"), name("feat/b")))
                        .withBaseDistance(HashMap.of(
                                name("feat/c"),
                                new BaseDistance(new BranchRef.Local(name("feat/b")), new Sha("bbbb"), 1, 2))));

        String screen = render(lagging, new StackPopups.Pick(CHOICE, Option.none(), Option.none()));

        assertTrue(screen.contains("feat/c  #98  ⇣2 not on feat/b"), screen);
    }

    @Test
    void aRefusedBottomSaysWhy() throws Exception {
        String screen = render(new StackPopups.Pick(CHOICE, Option.of("dialog.makeStack.bottomAbove"), Option.none()));

        assertTrue(screen.contains("The bottom is this branch or one below it"), screen);
    }

    private String render(StackPopups.Pick pick) throws Exception {
        return render(TestModels.loaded(), pick);
    }

    private String render(Model model, StackPopups.Pick pick) throws Exception {
        Model open = model.openPopup(pick).withListCursor(ListCursor.START.at(1));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            return RenderedText.ofLarge(runner, () -> view(open));
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
