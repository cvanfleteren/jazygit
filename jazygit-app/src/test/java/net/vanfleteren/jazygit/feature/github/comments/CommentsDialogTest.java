package net.vanfleteren.jazygit.feature.github.comments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.github.comment.CommentMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestComment.Kind;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import org.junit.jupiter.api.Test;

/**
 * The popup with the conversation on a pull request: the list beside the highlighted comment, or above it on a narrow
 * screen, and the keys that move through it, scroll it, comment, open and go back.
 */
class CommentsDialogTest {

    private static final PullRequestPopup SHOWN = PullRequestPopup.loading(
                    new BranchName("feature"),
                    new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED),
                    Instant.EPOCH,
                    0)
            .with(
                    Either.right(StubGitHub.of()
                            .pullRequests()
                            .details(new PrNumber(42))
                            .get()),
                    0);
    private static final Size WIDE = new Size(140, 30);
    // Under 100 columns, the list is above the comment, a third of the popup high.
    private static final Size NARROW = new Size(90, 24);
    private static final Instant AT = Instant.parse("2026-10-10T08:00:00Z");
    private static final PullRequestComment DESCRIPTION = comment(
            PullRequestComment.DESCRIPTION.value(), Kind.DESCRIPTION, "cvf", "Adds renaming.<!-- bot marker -->");
    private static final PullRequestComment FIRST = comment("IC_1", Kind.COMMENT, "bob", "## Looks fine\n\nShip it");
    private static final PullRequestComment APPROVAL = comment("PRR_1", Kind.APPROVED, "carol", "");
    private static final List<PullRequestComment> CONVERSATION = List.of(DESCRIPTION, FIRST, APPROVAL);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = reading(CONVERSATION);
    private final CommentsDialog dialog = new CommentsDialog(dispatched::add, Clock.fixed(AT, ZoneOffset.UTC));

    private static PullRequestComment comment(String id, Kind kind, String author, String body) {
        return new PullRequestComment(
                new PullRequestComment.Id(id), kind, author, AT, body, false, Option.none(), CommentRights.ALL);
    }

    private static Model reading(List<PullRequestComment> comments) {
        return TestModels.loaded().openPopup(CommentsPopup.loading(SHOWN).read(Either.right(comments)));
    }

    @Test
    void onAWideScreenTheListIsBesideTheHighlightedComment() throws Exception {
        model = model.openPopup(model.popup(CommentsPopup.class).get().withHighlighted(Option.of(FIRST.id())));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofSize(runner, this::view, 140, 30);

            assertTrue(screen.contains("Comments on pull request #42"), screen);
            String row = line(screen, "  @bob  ");
            assertTrue(row.contains("Looks fine"), screen);
            assertTrue(screen.contains("@bob · 2026-10-10 08:00"), screen);
            assertTrue(line(screen, "@cvf").contains("description"), screen);
            assertTrue(screen.contains("Ship it"), screen);
            assertTrue(screen.contains("(j/k) move"), screen);
        }
    }

    @Test
    void anImageInTheHighlightedCommentIsAPlaceholderNotItsTag() throws Exception {
        PullRequestComment working = comment(
                "IC_2",
                Kind.COMMENT,
                "claude",
                "Claude Code is working… <img src=\"https://github.com/user-attachments/assets/5ac3\""
                        + " width=\"14px\" height=\"14px\" style=\"vertical-align: middle; margin-left: 4px;\" />");
        model = reading(List.of(working));
        model = model.openPopup(model.popup(CommentsPopup.class).get().withHighlighted(Option.of(working.id())));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofSize(runner, this::view, 140, 30);

            assertTrue(screen.contains("Claude Code is working… [image]"), screen);
            assertFalse(screen.contains("<img"), screen);
            assertFalse(screen.contains("user-attachments"), screen);
        }
    }

    @Test
    void onANarrowScreenTheListIsAboveTheComment() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofSize(runner, this::view, 90, 30);

            List<String> lines = List.of(screen.split("\n"));
            int carol = lines.indexWhere(text -> text.contains("@carol") && !text.contains("·"));
            int title = lines.indexWhere(text -> text.contains("@carol · 2026-10-10 08:00 · approved"));
            assertTrue(carol >= 0 && title > carol, screen);
            assertTrue(screen.contains("No text."), screen);
        }
    }

    @Test
    void theAuthorsLineUpAfterTheSymbol() throws Exception {
        model = reading(List.of(
                APPROVAL,
                comment("PRR_2", Kind.DISMISSED, "dave", "Hmm"),
                comment("PRR_3", Kind.COMMENTED, "erin", "Looks fine")));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofSize(runner, this::view, 140, 30);

            // The symbols of an approval, a dismissed review and a review that commented take one column each.
            String approved = line(screen, "@carol  ");
            String dismissed = line(screen, "@dave  ");
            String commented = line(screen, "@erin  ");
            assertTrue(approved.contains("+ @carol"), screen);
            assertTrue(commented.contains("~ @erin"), screen);
            assertEquals(column(approved, "@carol"), column(dismissed, "@dave"), screen);
            assertEquals(column(approved, "@carol"), column(commented, "@erin"), screen);
        }
    }

    @Test
    void htmlCommentsAreLeftOut() throws Exception {
        model = model.openPopup(model.popup(CommentsPopup.class).get().withHighlighted(Option.of(DESCRIPTION.id())));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofSize(runner, this::view, 140, 30);

            assertTrue(screen.contains("Adds renaming."), screen);
            assertFalse(screen.contains("bot marker"), screen);
        }
    }

    @Test
    void whileTheCommentsAreReadOrWhenThatFailedItSaysSo() throws Exception {
        model = TestModels.loaded().openPopup(CommentsPopup.loading(SHOWN));
        try (ToolkitTestRunner runner = start()) {
            String loading = RenderedText.ofSize(runner, this::view, 140, 30);
            assertTrue(loading.contains("Reading the comments from GitHub…"), loading);

            model = model.openPopup(CommentsPopup.loading(SHOWN).read(Either.left(TestModels.refused("offline"))));
            String failed = RenderedText.ofSize(runner, this::view, 140, 30);
            assertTrue(failed.contains("Could not read the comments"), failed);

            model = reading(List.empty());
            String none = RenderedText.ofSize(runner, this::view, 140, 30);
            assertTrue(none.contains("No description, comments or reviews yet."), none);
        }
    }

    @Test
    void theKeysMoveThroughTheCommentsCommentOpenAndGoBack() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // On the newest: j goes nowhere, k to the one before it, Home to the first.
            SettledPilot.of(runner).press('j');
            SettledPilot.of(runner).press('k');
            SettledPilot.of(runner).press(KeyCode.HOME);
            SettledPilot.of(runner).press('o');
            SettledPilot.of(runner).press('c');
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new CommentsMsg.Highlighted(FIRST.id()),
                        new CommentsMsg.Highlighted(DESCRIPTION.id()),
                        new CommentsMsg.OpenChosen(),
                        new CommentMsg.Requested(),
                        new CommentsMsg.Closed()),
                dispatched);
    }

    @Test
    void theHighlightedCommentScrollsAsFarAsItsEnd() throws Exception {
        String body = List.range(1, 61).map(i -> "Paragraph " + i).mkString("\n\n");
        model = reading(List.of(comment("IC_1", Kind.COMMENT, "bob", body)));
        try (ToolkitTestRunner runner = start()) {
            String top = RenderedText.ofSize(runner, this::view, 140, 30);
            // The pane of the comment starts right after the border of the list.
            assertTrue(top.contains("││Paragraph 1 "), top);
            assertFalse(top.contains("Paragraph 60"), top);

            for (int i = 0; i < 20; i++) {
                SettledPilot.of(runner).press(KeyCode.PAGE_DOWN);
            }
            SettledPilot.of(runner).pause();
            String end = RenderedText.ofSize(runner, this::view, 140, 30);
            assertTrue(end.contains("Paragraph 60"), end);
            assertFalse(end.contains("││Paragraph 1 "), end);

            SettledPilot.of(runner).press('K');
            SettledPilot.of(runner).pause();
            String back = RenderedText.ofSize(runner, this::view, 140, 30);
            assertFalse(back.contains("Paragraph 60"), back);
        }
    }

    @Test
    void theFirstLineOfACommentIsItsFirstText() {
        assertEquals("Review", CommentsDialog.firstLine("<!-- marker -->\n\n## Review\n\n> quoted"));
        assertEquals("quoted", CommentsDialog.firstLine("> quoted"));
        assertEquals("item", CommentsDialog.firstLine("  - item"));
        assertEquals(
                "Working… [image]",
                CommentsDialog.firstLine("Working… <img src=\"https://github.com/a.gif\" width=\"14px\" />"));
        assertEquals("", CommentsDialog.firstLine(""));
        // Only a marker followed by a space is one.
        assertEquals("**Note**: x", CommentsDialog.firstLine("**Note**: x"));
        assertEquals("+1", CommentsDialog.firstLine("+1"));
        assertEquals("-1", CommentsDialog.firstLine("-1"));
        assertEquals("nested", CommentsDialog.firstLine("> - nested"));
    }

    @Test
    void clickingARowHighlightsIt() throws Exception {
        try (ToolkitTestRunner runner = start(WIDE)) {
            int[] description = at(RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height()), "@cvf");

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, description[0], description[1]);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommentsMsg.Highlighted(DESCRIPTION.id())), dispatched);
    }

    @Test
    void clickingARowOfAScrolledListHighlightsTheRowShownThere() throws Exception {
        // On a narrow screen the list takes a third of the popup, too little for all of them; on the newest, it
        // shows the last few.
        model = reading(List.range(0, 20).map(i -> comment("IC_" + i, Kind.COMMENT, "user" + i, "comment " + i)));
        try (ToolkitTestRunner runner = start(NARROW)) {
            String screen = RenderedText.ofSize(runner, this::view, NARROW.width(), NARROW.height());
            assertFalse(screen.contains("@user10 "), screen);
            int[] shown = at(screen, "@user17 ");

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, shown[0], shown[1]);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommentsMsg.Highlighted(new PullRequestComment.Id("IC_17"))), dispatched);
    }

    @Test
    void theWheelOverTheListMovesTheHighlightThreeComments() throws Exception {
        model = reading(List.range(0, 6).map(i -> comment("IC_" + i, Kind.COMMENT, "user" + i, "comment " + i)));
        model = model.openPopup(
                model.popup(CommentsPopup.class).get().withHighlighted(Option.of(new PullRequestComment.Id("IC_0"))));
        try (ToolkitTestRunner runner = start(WIDE)) {
            int[] row = at(RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height()), "@user1 ");

            route(runner, MouseEvent.scrollDown(row[0], row[1]));
        }

        assertEquals(java.util.List.of(new CommentsMsg.Highlighted(new PullRequestComment.Id("IC_3"))), dispatched);
    }

    @Test
    void theWheelOverTheCommentScrollsIt() throws Exception {
        String body = List.range(1, 61).map(i -> "Paragraph " + i).mkString("\n\n");
        model = reading(List.of(comment("IC_1", Kind.COMMENT, "bob", body)));
        try (ToolkitTestRunner runner = start(WIDE)) {
            String top = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());
            int[] text = at(top, "││Paragraph 3 ");

            route(runner, MouseEvent.scrollDown(text[0] + 4, text[1]));
            String scrolled = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());

            assertTrue(top.contains("││Paragraph 1 "), top);
            assertFalse(scrolled.contains("││Paragraph 1 "), scrolled);
            assertTrue(scrolled.contains("││Paragraph 3 "), scrolled);
        }
        assertEquals(java.util.List.of(), dispatched);
    }

    @Test
    void clickingALinkInTheCommentOpensIt() throws Exception {
        model = reading(List.of(comment(
                "IC_1",
                Kind.COMMENT,
                "bob",
                "See [the docs](HTTPS://example.com/docs), [below](#notes) or plain text")));
        try (ToolkitTestRunner runner = start(WIDE)) {
            String screen = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());
            // In the comment, not in its row of the list, which shows the Markdown as it is written.
            int[] shown = at(screen, "││See the docs, below or plain text");
            int start = shown[0] + "││".length();
            int y = shown[1];

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "See the d".length(), y);
            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "See the docs, b".length(), y);
            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "See the docs, below or p".length(), y);
            SettledPilot.of(runner).pause();
        }

        // A scheme is the same in any case. A link within the page, which a browser would not know where to find,
        // opens nothing; nor does plain text.
        assertEquals(java.util.List.of(new CommentsMsg.LinkChosen("HTTPS://example.com/docs")), dispatched);
    }

    @Test
    void clickingAReferenceOrABareURLInTheCommentOpensIt() throws Exception {
        PullRequestPopup ofRepository = SHOWN.with(
                Either.right(StubGitHub.of()
                        .pullRequests()
                        .details(new PrNumber(42))
                        .get()
                        .withRepository("owner/repo")
                        .withUrl("https://github.com/owner/repo")),
                0);
        model = TestModels.loaded()
                .openPopup(CommentsPopup.loading(ofRepository)
                        .read(Either.right(List.of(comment(
                                "IC_1", Kind.COMMENT, "bob", "Fixed in #381, see https://example.com/docs.")))));
        try (ToolkitTestRunner runner = start(WIDE)) {
            String screen = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());
            // Each shows as it is written.
            int[] shown = at(screen, "││Fixed in #381, see https://example.com/docs.");
            int start = shown[0] + "││".length();
            int y = shown[1];

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "Fixed in #3".length(), y);
            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "Fixed in #381, see https://ex".length(), y);
            // The full stop after the URL is not part of it.
            SettledPilot.of(runner)
                    .mousePress(MouseButton.LEFT, start + "Fixed in #381, see https://example.com/docs".length(), y);
            SettledPilot.of(runner).pause();
        }

        // GitHub sends /issues/381 on to /pull/381 when it is a pull request (ADR 98).
        assertEquals(
                java.util.List.of(
                        new CommentsMsg.LinkChosen("https://github.com/owner/repo/issues/381"),
                        new CommentsMsg.LinkChosen("https://example.com/docs")),
                dispatched);
    }

    @Test
    void beforeItsDetailsAreReadAReferenceIsToTheRepositoryAsItWasListed() throws Exception {
        PullRequestPopup listed = PullRequestPopup.loading(
                new BranchName("feature"),
                new PullRequest(
                        new PrNumber(42),
                        new GhBranch("feature"),
                        Option.of(new GhBranch("main")),
                        false,
                        Checks.PASSING,
                        Review.APPROVED,
                        Option.none(),
                        PullRequest.Commits.of(
                                "owner/repo",
                                "https://github.com/owner/repo/pull/42",
                                Option.of(new GhCommit("b".repeat(40))),
                                Option.of(new GhCommit("h".repeat(40))))),
                Instant.EPOCH,
                0);

        clickReferenceAndURL(listed);

        assertEquals(
                java.util.List.of(
                        new CommentsMsg.LinkChosen("https://github.com/owner/repo/issues/381"),
                        new CommentsMsg.LinkChosen("https://example.com/docs")),
                dispatched);
    }

    @Test
    void withoutARepositoryAReferenceOpensNothingButAURLDoes() throws Exception {
        // The stub's details say no repository, nor does the pull request as it was listed.
        clickReferenceAndURL(SHOWN);

        assertEquals(java.util.List.of(new CommentsMsg.LinkChosen("https://example.com/docs")), dispatched);
    }

    // Clicks #381, then the bare URL, of a comment on the pull request of shown.
    private void clickReferenceAndURL(PullRequestPopup shown) throws Exception {
        model = TestModels.loaded()
                .openPopup(CommentsPopup.loading(shown)
                        .read(Either.right(List.of(comment(
                                "IC_1", Kind.COMMENT, "bob", "Fixed in #381, see https://example.com/docs.")))));
        try (ToolkitTestRunner runner = start(WIDE)) {
            String screen = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());
            int[] shownAt = at(screen, "││Fixed in #381, see https://example.com/docs.");
            int start = shownAt[0] + "││".length();

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, start + "Fixed in #3".length(), shownAt[1]);
            SettledPilot.of(runner)
                    .mousePress(MouseButton.LEFT, start + "Fixed in #381, see https://ex".length(), shownAt[1]);
            SettledPilot.of(runner).pause();
        }
    }

    @Test
    void aLinkWrappedOverLinesOpensFromItsNextLine() throws Exception {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho "
                + "sigma tau upsilon phi chi psi omega";
        model = reading(List.of(comment("IC_1", Kind.COMMENT, "bob", "See [" + text + "](https://example.com/long)")));
        try (ToolkitTestRunner runner = start(NARROW)) {
            String screen = RenderedText.ofSize(runner, this::view, NARROW.width(), NARROW.height());
            int[] first = at(screen, "│See alpha");
            int[] last = at(screen, "omega");
            assertTrue(last[1] > first[1], screen);

            SettledPilot.of(runner).mousePress(MouseButton.LEFT, last[0] + 1, last[1]);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommentsMsg.LinkChosen("https://example.com/long")), dispatched);
    }

    @Test
    void aLinkOpensFromWhereTheScrolledCommentShowsIt() throws Exception {
        String body = "Intro\n\nMore\n\n[the docs](https://example.com/docs)\n\n"
                + List.range(1, 61).map(i -> "Paragraph " + i).mkString("\n\n");
        model = reading(List.of(comment("IC_1", Kind.COMMENT, "bob", body)));
        try (ToolkitTestRunner runner = start(WIDE)) {
            int[] before = at(RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height()), "││the docs");

            SettledPilot.of(runner).press('J');
            SettledPilot.of(runner).press('J');
            String scrolled = RenderedText.ofSize(runner, this::view, WIDE.width(), WIDE.height());
            int[] after = at(scrolled, "││the docs");
            assertEquals(before[1] - 2, after[1], scrolled);

            // Where the link was before, the comment now shows a paragraph.
            SettledPilot.of(runner).mousePress(MouseButton.LEFT, before[0] + 4, before[1]);
            SettledPilot.of(runner).mousePress(MouseButton.LEFT, after[0] + 4, after[1]);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommentsMsg.LinkChosen("https://example.com/docs")), dispatched);
    }

    private static String line(String screen, String containing) {
        return List.of(screen.split("\n"))
                .find(text -> text.contains(containing))
                .getOrElse("");
    }

    /**
     * The screen column {@code text} first shows at on {@code line}, counting a wide character as the columns it takes.
     */
    private static int column(String line, String text) {
        return TextWidth.of(line.substring(0, line.indexOf(text)));
    }

    /**
     * Where {@code text} first shows on {@code screen}, as its column and line.
     */
    private static int[] at(String screen, String text) {
        List<String> lines = List.of(screen.split("\n"));
        int y = lines.indexWhere(line -> line.contains(text));
        assertTrue(y >= 0, screen);
        return new int[] {lines.get(y).indexOf(text), y};
    }

    private static void route(ToolkitTestRunner runner, MouseEvent event) {
        runner.runner().runOnRenderThread(() -> runner.runner().eventRouter().route(event));
        SettledPilot.settle(runner);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, CommentsDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    /**
     * A runner whose screen is {@code size}, as mouse events land where its last render put things.
     */
    private ToolkitTestRunner start(Size size) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view, size);
        SettledPilot.focus(runner, CommentsDialog.ID);
        SettledPilot.settle(runner);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
