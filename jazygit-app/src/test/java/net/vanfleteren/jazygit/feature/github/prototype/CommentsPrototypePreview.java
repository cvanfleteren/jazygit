package net.vanfleteren.jazygit.feature.github.prototype;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PrototypeComment;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * PROTOTYPE: not a test; prints the three layouts of the comments dialog, to look at them.
 * Run: mvn -pl jazygit-app test -Dtest=CommentsPrototypePreview -Dsurefire.failIfNoSpecifiedTests=false
 */
class CommentsPrototypePreview {

    private static final PullRequestPopup SHOWN = PullRequestPopup.loading(
                    new Branch.Name("worktree-pr-comment"),
                    new PullRequest(33, "worktree-pr-comment", false, Checks.PASSING, Review.APPROVED))
            .with(Either.right(StubGitHub.empty().pullRequestDetails(33).get()));

    private static final List<PrototypeComment> COMMENTS = List.of(
            new PrototypeComment(
                    "cvanfleteren",
                    Instant.parse("2026-10-18T10:00:00Z"),
                    "",
                    "Adds a **Comment** action to the PR dialog.\n\n- `c` opens a popup\n- Ctrl+S sends",
                    "https://example.org/1"),
            new PrototypeComment(
                    "claude",
                    Instant.parse("2026-10-19T10:00:00Z"),
                    "CHANGES_REQUESTED",
                    "## Review\n\nAddComment marks the branch **busy**, which overwrites the mark of a draft"
                            + " change still running:\n\n> marking it overwrote the mark of a draft change\n\n"
                            + "```java\nreturn Update.start(model.withBusy(branch), cmd);\n```\n\n"
                            + "<!-- claude-review-marker -->\n| File | Line |\n|---|---|\n| CommentUpdate.java | 39 |\n\n"
                            + "Suggest not marking it busy at all.\n\n" + "More text. ".repeat(80),
                    ""),
            new PrototypeComment("cvanfleteren", Instant.parse("2026-10-20T08:00:00Z"), "", "Fixed in 57a7810.", "u"),
            new PrototypeComment("claude", Instant.parse("2026-10-20T21:00:00Z"), "APPROVED", "LGTM 🎉", ""));

    private Model model =
            TestModels.loaded().openPopup(new CommentsPrototypePopup(SHOWN, Option.of(Either.right(COMMENTS))));
    private final CommentsPrototypeDialog dialog = new CommentsPrototypeDialog(msg -> {});

    @Test
    void print() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, CommentsPrototypeDialog.ID);
            RenderedText.ofTall(runner, this::view);
            System.out.println(RenderedText.ofTall(runner, this::view));
            SettledPilot.of(runner).press('k');
            SettledPilot.of(runner).press('k');
            SettledPilot.of(runner).press('J');
            System.out.println(RenderedText.ofTall(runner, this::view));
            for (int i = 0; i < 2; i++) {
                SettledPilot.of(runner).press(']');
                System.out.println(RenderedText.ofTall(runner, this::view));
            }
        }
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
