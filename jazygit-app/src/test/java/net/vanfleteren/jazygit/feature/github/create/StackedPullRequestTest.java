package net.vanfleteren.jazygit.feature.github.create;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.stack.StackCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.Stacking;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

/**
 * A new pull request on a branch with an open pull request is stacked on it once it is created, unless the user
 * unticks it; when that one is in a stack but not on its top, the dialog says why it cannot be (ADR 73).
 */
class StackedPullRequestTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName FEAT_A = new BranchName("feat/a");
    private static final BranchName MAIN = new BranchName("main");

    private static PullRequest pr(int number, String head, Option<PullRequestStack> stack) {
        return new PullRequest(
                number,
                new GhBranch(head),
                new GhBranch("main"),
                false,
                Checks.NONE,
                Review.NONE,
                stack,
                Option.none());
    }

    // feat/a has #96 open, on whatever stack is given; feature has none yet.
    private static Model model(Option<PullRequestStack> stack) {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(MAIN, FEAT_A, FEATURE).map(name -> Fixtures.branch(name, false, new Sha("aaaa"))))
                .model();
        return read.mapGitHub(
                gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(FEAT_A, pr(96, "feat/a", stack)))));
    }

    private static PullRequestStack stackOf(int position) {
        return new PullRequestStack(
                12,
                position,
                2,
                List.of(
                        new PullRequestStack.Entry(1, 96, State.OPEN, new GhBranch("feat/a"), new GhBranch("main")),
                        new PullRequestStack.Entry(2, 95, State.OPEN, new GhBranch("other"), new GhBranch("feat/a"))));
    }

    private static NewPullRequestPopup popup(BranchName base, boolean stacked) {
        return new NewPullRequestPopup(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                List.of(FEAT_A, MAIN),
                base,
                "Add b",
                new NewPullRequest("Add b", "", false),
                stacked);
    }

    @Test
    void aBaseWithoutAPullRequestOffersNoStack() {
        assertEquals(Option.none(), CreatePullRequestUpdate.stackOn(model(Option.none()), MAIN));
    }

    @Test
    void onAPullRequestInNoStackTheTwoMakeOne() {
        assertEquals(
                Option.of(Either.right(new Stacking(96, Option.none()))),
                CreatePullRequestUpdate.stackOn(model(Option.none()), FEAT_A));
    }

    @Test
    void onTheTopOfAStackItIsAddedToIt() {
        PullRequestStack top = new PullRequestStack(
                12,
                1,
                1,
                List.of(new PullRequestStack.Entry(1, 96, State.OPEN, new GhBranch("feat/a"), new GhBranch("main"))));

        assertEquals(
                Option.of(Either.right(new Stacking(96, Option.of(12)))),
                CreatePullRequestUpdate.stackOn(model(Option.of(top)), FEAT_A));
    }

    @Test
    void notOnTheTopOfAStackItSaysWhy() {
        assertEquals(
                Option.of(Either.left(new CreatePullRequestCmd.NotTop(96, 12))),
                CreatePullRequestUpdate.stackOn(model(Option.of(stackOf(1))), FEAT_A));
    }

    @Test
    void creatingItTickedCarriesTheStackingAndUntickedNone() {
        Model model = model(Option.none());

        Update.Next ticked = Update.update(model.openPopup(popup(FEAT_A, true)), new CreatePullRequestMsg.Confirmed());
        Update.Next unticked = Update.update(
                Update.update(model.openPopup(popup(FEAT_A, true)), new CreatePullRequestMsg.StackToggled())
                        .model(),
                new CreatePullRequestMsg.Confirmed());

        assertEquals(
                List.of(Option.of(new Stacking(96, Option.none()))),
                ticked.cmds().map(cmd -> ((CreatePullRequest) cmd).stacking()));
        assertEquals(
                List.of(Option.<Stacking>none()), unticked.cmds().map(cmd -> ((CreatePullRequest) cmd).stacking()));
    }

    @Test
    void onceCreatedItIsStackedOnThePullRequestOfItsBase() {
        CreatePullRequest cmd = new CreatePullRequest(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                new RemoteBranch("origin", FEAT_A),
                new NewPullRequest("Add b", "", false),
                Option.of(new Stacking(96, Option.none())));
        Model running = TestModels.running(model(Option.none()), cmd);

        Update.Next created = Update.update(
                running,
                new CreatePullRequestMsg.Created(cmd, Option.of(pr(101, "feature", Option.none())), List.empty()));

        assertTrue(
                created.cmds().contains(new StackCmd.MakeStack(List.empty(), List.of(96, 101), Option.none())),
                created.cmds().toString());
    }

    @Test
    void theDialogShowsTheBoxTickedOrWhyItCannotBe() throws Exception {
        assertTrue(render(model(Option.none()).openPopup(popup(FEAT_A, true))).contains("[x] Stack on #96"));
        String notTop = render(model(Option.of(stackOf(1))).openPopup(popup(FEAT_A, true)));
        assertTrue(notTop.contains("[ ] Stack on #96 · #96 is not the top of stack #12"), notTop);
        assertTrue(!render(model(Option.none()).openPopup(popup(MAIN, true))).contains("Stack on"));
    }

    private static String render(Model open) throws Exception {
        NewPullRequestDialog dialog = new NewPullRequestDialog(msg -> {});
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(dialog, open))) {
            return RenderedText.ofLarge(runner, () -> view(dialog, open));
        }
    }

    private static Element view(NewPullRequestDialog dialog, Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
