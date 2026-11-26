package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.features.github.prs.content.PullRequestBody;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A pull request in the content panel: its description and diff, the keys of the cursor of its diff, and the
 * ranges of lines a review comments on.
 */
public class PullRequestContentTest {

    @Test
    void theKeysOfTheDiffOfAPullRequestActOnItsCursor() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg)));
        Model model = pullRequestDiff();
        AreaId area = reviewing.areaOfPane(model, FilesPanel.PANE).get();

        // The model the app draws, which only knows where the cursor is once it handled the message that says so.
        java.util.concurrent.atomic.AtomicReference<Model> drawn =
                new java.util.concurrent.atomic.AtomicReference<>(model);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(drawn.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            ViewMsg.ContentScrolled next = (ViewMsg.ContentScrolled) dispatched.getLast();
            Anchor onThread = new Anchor.Thread(
                    new net.vanfleteren.jazygit.kernel.state.diff.RemarkThread.Id("T1"),
                    Option.of(new Anchor.OnLine("a.txt", DiffSide.NEW, 1)));
            assertEquals(Option.of(onThread), next.to().cursor());

            drawn.set(Update.update(model, next).model());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('x');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    onThread,
                    ((net.vanfleteren.jazygit.features.github.review.ReviewMsg.ResolveAsked) dispatched.getLast())
                            .at()
                            .cursor());

            // Up to its line, and a thread started there, before the model knows the cursor moved.
            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('c');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    new Anchor.OnLine("a.txt", DiffSide.NEW, 1),
                    ((net.vanfleteren.jazygit.features.github.review.ReviewMsg.StartAsked) dispatched.getLast())
                            .at()
                            .cursor());
        }
    }

    @Test
    void inTheDiffOfAPullRequestYCopiesAndVStaysTheKeyOfTheReview() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg)));
        Model model = pullRequestDiff();
        AreaId area = reviewing.areaOfPane(model, FilesPanel.PANE).get();
        Anchor.OnLine line = new Anchor.OnLine("a.txt", DiffSide.NEW, 1);

        java.util.concurrent.atomic.AtomicReference<Model> drawn =
                new java.util.concurrent.atomic.AtomicReference<>(model);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(drawn.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            drawn.set(Update.update(model, dispatched.getLast()).model());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));

            // On a thread, v is the review's: it says why no range opens, where the key of every rich diff says
            // nothing.
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    new net.vanfleteren.jazygit.features.github.review.ReviewMsg.Refused(
                            net.vanfleteren.jazygit.features.github.GitHubError.CannotReview.Why.NOT_ON_LINE),
                    dispatched.getLast());

            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('y');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked.class::cast);
        assertEquals(List.of(line), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area), asked.map(msg -> msg.at().area()));
    }

    @Test
    void lScrollsTheDiffOfAPullRequestSidewaysWithItsCursorOnAThreadToAWideSuggestion() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                pullRequestDiff("Wrap it:\n\n```suggestion\n" + "0123456789" + "x".repeat(300) + "\n```"));
        ContentPanel reviewing = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg));
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = reviewing.areaOfPane(model.get(), FilesPanel.PANE).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('l');
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
        }

        assertEquals(1, ((ViewMsg.ContentScrolled) dispatched.getLast()).to().column(), dispatched.toString());
        // The whole diff shows a column further, its bar too, which starts with a blank.
        assertTrue(screen.contains("│modified  a.txt"), screen);
        assertTrue(screen.contains("│0123456789x"), screen);
    }

    @Test
    void leftDropsAnOpenRangeBeforeItLeavesTheDiffAsEscapeDoes() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(pullRequestDiff());
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                msg -> {
                    dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg));
                    model.set(Update.update(model.get(), msg).model());
                });
        AreaId area = reviewing.areaOfPane(model.get(), FilesPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            // On to the thread, up to its line, and a range opened there.
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            assertTrue(
                    ((ViewMsg.ContentScrolled) dispatched.getLast()).to().mark().isDefined());

            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            assertEquals(
                    Option.none(),
                    ((ViewMsg.ContentScrolled) dispatched.getLast()).to().mark());

            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
    }

    @Test
    void aRangeShowsItsHunkAsGitGivesItWithTheLineOfTheCursorWhereItWas() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                pullRequestDiff("Why?", """
                        diff --git a/a.txt b/a.txt
                        --- a/a.txt
                        +++ b/a.txt
                        @@ -1,42 +1,42 @@
                        """ + " context\n".repeat(40) + """
                                -int count = 1;
                                -int total = 2;
                                +int count = 42;
                                +int total = 43;
                                """));
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = reviewing.areaOfPane(model.get(), FilesPanel.PANE).get();

        String paired;
        String ranged;
        String dropped;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            // To the end of the diff, scrolled down, and up from its marker to the second removed line.
            SettledPilot.of(testRunner).press(KeyCode.END);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            for (int step = 0; step < 2; step++) {
                SettledPilot.of(testRunner).press('k');
                SettledPilot.of(testRunner).pause();
                RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            }
            paired = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            ranged = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            dropped = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
        }

        assertTrue(
                ContentPanelFixtures.row(paired, "- int count") < ContentPanelFixtures.row(paired, "+ int count")
                        && ContentPanelFixtures.row(paired, "+ int count")
                                < ContentPanelFixtures.row(paired, "- int total"),
                paired);
        assertTrue(
                ContentPanelFixtures.row(ranged, "- int count") < ContentPanelFixtures.row(ranged, "- int total")
                        && ContentPanelFixtures.row(ranged, "- int total")
                                < ContentPanelFixtures.row(ranged, "+ int count"),
                ranged);
        assertEquals(
                ContentPanelFixtures.row(paired, "- int total"),
                ContentPanelFixtures.row(ranged, "- int total"),
                paired + ranged);
        assertEquals(
                ContentPanelFixtures.row(paired, "- int total"),
                ContentPanelFixtures.row(dropped, "- int total"),
                paired + dropped);
        assertTrue(
                ContentPanelFixtures.row(dropped, "+ int count") < ContentPanelFixtures.row(dropped, "- int total"),
                dropped);
    }

    /**
     * A pull request's files open in the files pane, in the rich view, its one file selected: a diff of one added
     * line, with a thread on it.
     */
    private static Model pullRequestDiff() {
        return pullRequestDiff("Why?");
    }

    /**
     * {@link #pullRequestDiff()}, the one comment of its thread saying {@code body}.
     */
    private static Model pullRequestDiff(String body) {
        return pullRequestDiff(body, """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -0,0 +1 @@
                +a
                """);
    }

    /**
     * {@link #pullRequestDiff(String)}, of the diff {@code diff}, with the thread on the first line of the new file.
     */
    static Model pullRequestDiff(String body, String diff) {
        Changes of = pullRequestChanges();
        CommitFile file = new CommitFile("a.txt", ChangeType.MODIFIED);
        Model rich = PullRequestContentTest.withGitHub()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model open = Update.update(
                        Update.update(
                                        Update.update(
                                                        rich,
                                                        new net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg
                                                                .Opened(of, Scopes.BRANCHES.pane()))
                                                .model(),
                                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                                .model(),
                        remarksLoaded(body))
                .model();
        return Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), rich.preferences().diffOptions(), DiffText.of(diff)))
                .model();
    }

    /**
     * The sample model, with GitHub's feature answering what happens in the kernel, as it reads the review threads of
     * the files of a pull request once they are opened (ADR 171).
     */
    public static Model withGitHub() {
        return TestModels.loaded()
                .withReactions(new net.vanfleteren.jazygit.kernel.state.Reactions(
                        net.vanfleteren.jazygit.features.github.GitHubFeature.FEATURE.reactions()));
    }

    private static Changes pullRequestChanges() {
        return new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), new RepoSlug("owner/repo"), "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
    }

    /**
     * The review threads of the pull request of {@link #pullRequestDiff(String, String)} read: one on the first line of
     * the new file, its one comment saying {@code body}.
     */
    static Msg remarksLoaded(String body) {
        net.vanfleteren.jazygit.core.gh.model.ReviewThread thread =
                new net.vanfleteren.jazygit.core.gh.model.ReviewThread(
                        "a.txt",
                        net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT,
                        Option.of(1),
                        Option.none(),
                        net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT,
                        Option.of(1),
                        false,
                        false,
                        net.vanfleteren.jazygit.core.gh.model.ReviewThread.Subject.LINE,
                        List.of(new net.vanfleteren.jazygit.core.gh.model.ReviewThread.Comment(
                                "bob",
                                Instant.EPOCH,
                                body,
                                Option.none(),
                                new net.vanfleteren.jazygit.core.gh.model.ReviewThread.Comment.Id("C"),
                                new net.vanfleteren.jazygit.core.gh.model.ReviewThread.Comment.RestId("1"),
                                net.vanfleteren.jazygit.core.gh.model.CommentRights.ALL)),
                        0,
                        new net.vanfleteren.jazygit.core.gh.model.ReviewThread.Id("T1"),
                        net.vanfleteren.jazygit.core.gh.model.ReviewThread.Rights.ALL);
        return new net.vanfleteren.jazygit.features.github.remarks.RemarksMsg.Loaded(
                new PrNumber(7),
                new net.vanfleteren.jazygit.core.gh.model.ReviewRemarks(
                        net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State.OPEN,
                        net.vanfleteren.jazygit.core.repo.Sha.parse("a".repeat(40)),
                        net.vanfleteren.jazygit.core.repo.Sha.parse("b".repeat(40)),
                        List.of(thread)),
                HashSet.empty(),
                0);
    }

    @Test
    void aPullRequestShowsLikeACommitWithItsBranchesItsDescriptionAndItsDiff() {
        PullRequestSummary pr = new PullRequestSummary(
                new RepoSlug("owner/repo"),
                new PrNumber(12),
                "Fix the login",
                "alice",
                new ForgeBranch("fix/login"),
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.parse("2026-10-09T12:30:00Z"),
                "https://github.com/owner/repo/pull/12");

        List<String> lines = PullRequestBody.text(
                        pr,
                        new PullRequestPreview(new ForgeBranch("main"), "Why:\r\n\r\nit broke", "+fixed\n", false),
                        Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("pull request owner/repo#12", lines.get(0));
        assertEquals("Opened by alice", lines.get(1));
        assertEquals("From fix/login, in a fork, into main", lines.get(2));
        assertTrue(lines.get(3).startsWith("Updated "), lines.get(3));
        assertEquals(
                List.of("", "    Fix the login", "", "    Why:", "", "    it broke", "---", "+fixed"), lines.drop(4));
    }
}
