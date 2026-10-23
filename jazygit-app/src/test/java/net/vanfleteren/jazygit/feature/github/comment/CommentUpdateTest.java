package net.vanfleteren.jazygit.feature.github.comment;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd.AddComment;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd.EditComment;
import net.vanfleteren.jazygit.feature.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.feature.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.SetDraft;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Commenting on the pull request that is shown: writing the comment, in the popup or in the editor, and sending it.
 */
class CommentUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);
    private static final PullRequestDetails DETAILS =
            StubGitHub.empty().pullRequestDetails(42).get();
    private static final PullRequestPopup SHOWN =
            PullRequestPopup.loading(FEATURE, FEATURE_PR).with(Either.right(DETAILS));
    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "PR_COMMENT.md");

    private static Model showing() {
        return loaded().openPopup(SHOWN);
    }

    private static Model commenting(String text) {
        return loaded().openPopup(CommentPopup.on(SHOWN).withText(text));
    }

    private static Env env(StubGitProvider git, StubGitHub gitHub, Terminal terminal) {
        return new Env(git, gitHub, SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }

    @Test
    void commentingOpensAnEmptyCommentOnThePullRequestShown() {
        Next next = Update.update(showing(), new CommentMsg.Requested());

        assertEquals(Option.of(CommentPopup.on(SHOWN)), next.model().popup());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aClosedOrMergedPullRequestTakesCommentsToo() {
        PullRequestPopup merged = PullRequestPopup.loading(FEATURE, FEATURE_PR)
                .with(Either.right(new PullRequestDetails(
                        DETAILS.number(),
                        DETAILS.title(),
                        DETAILS.author(),
                        PullRequestDetails.State.MERGED,
                        DETAILS.draft(),
                        DETAILS.headBranch(),
                        DETAILS.baseBranch(),
                        DETAILS.createdAt(),
                        DETAILS.createdAt(),
                        DETAILS.additions(),
                        DETAILS.deletions(),
                        DETAILS.changedFiles(),
                        1,
                        DETAILS.mergeable(),
                        DETAILS.review(),
                        DETAILS.comments(),
                        DETAILS.reviewThreads(),
                        DETAILS.labels(),
                        DETAILS.reviewers(),
                        DETAILS.checks())));

        Next next = Update.update(loaded().openPopup(merged), new CommentMsg.Requested());

        assertEquals(Option.of(CommentPopup.on(merged)), next.model().popup());
    }

    @Test
    void noCommentBeforeTheDetailsAreRead() {
        Model loading = loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR));
        Model failed =
                loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR).with(Either.left(refused("offline"))));

        assertEquals(loading, Update.update(loading, new CommentMsg.Requested()).model());
        assertEquals(failed, Update.update(failed, new CommentMsg.Requested()).model());
    }

    @Test
    void whatIsTypedIsKeptInThePopup() {
        Model typed = Update.update(commenting(""), new CommentMsg.Edited("Looks good"))
                .model();

        assertEquals(Option.of(CommentPopup.on(SHOWN).withText("Looks good")), typed.popup());
    }

    @Test
    void sendingAddsTheCommentAsTypedAndKeepsThePopupOpenMeanwhile() {
        Next sending = Update.update(commenting("  Looks good\n"), new CommentMsg.Confirmed());

        assertEquals(List.of(new AddComment(FEATURE, 42, "  Looks good\n")), sending.cmds());
        assertEquals(
                Option.of(CommentPopup.on(SHOWN).withText("  Looks good\n").withSending(true)),
                sending.model().popup());
        // A comment does not touch the branch.
        assertEquals(Option.none(), sending.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void aCommentSentWhileTheDraftChangesLeavesTheBranchBusyUntilThatIsDone() {
        SetDraft drafting = new SetDraft(FEATURE, 42, true);
        Model busy = commenting("Looks good").withBusyBranch(new BranchRef.Local(FEATURE), PullRequestCmd.DRAFT_PR);
        Next sending = Update.update(busy, new CommentMsg.Confirmed());
        AddComment cmd = (AddComment) sending.cmds().head();
        Env env = env(new StubGitProvider() {}, StubGitHub.empty(), Terminal.NONE);

        Model sent = Update.update(sending.model(), cmd.run(env)).model();

        assertEquals(Option.of(PullRequestCmd.DRAFT_PR), sent.jobs().busy().get(new BranchRef.Local(FEATURE)));
        Model drafted = Update.update(sent, drafting.run(env)).model();
        assertEquals(Option.none(), drafted.jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void aDraftChangedWhileCommentingShowsInThePullRequestGoneBackTo() {
        SetDraft drafting = new SetDraft(FEATURE, 42, true);
        Model commenting = commenting("half a thought");

        Model drafted = Update.update(
                        commenting, drafting.run(env(new StubGitProvider() {}, StubGitHub.empty(), Terminal.NONE)))
                .model();
        Model back = Update.update(drafted, new CommentMsg.Cancelled()).model();

        assertEquals(Option.of(true), back.popup(PullRequestPopup.class).flatMap(PullRequestPopup::draft));
        // Changing the draft again turns it back.
        assertEquals(
                List.of(new SetDraft(FEATURE, 42, false)),
                Update.update(back, new PullRequestMsg.DraftChosen()).cmds());
    }

    @Test
    void detailsReadWhileCommentingShowInThePullRequestGoneBackTo() {
        Model commenting = loaded().openPopup(CommentPopup.on(PullRequestPopup.loading(FEATURE, FEATURE_PR)));

        Model read = Update.update(commenting, new PullRequestMsg.DetailsLoaded(42, DETAILS))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(FEATURE, FEATURE_PR).with(Either.right(DETAILS))),
                read.popup(CommentPopup.class).map(CommentPopup::from));
        // Details of another pull request change nothing.
        assertEquals(
                commenting,
                Update.update(commenting, new PullRequestMsg.DetailsLoaded(7, DETAILS))
                        .model());
    }

    @Test
    void whileItIsSentTheCommentCannotBeChangedSentAgainOrLeft() {
        Model sending = Update.update(commenting("Looks good"), new CommentMsg.Confirmed())
                .model();

        assertEquals(
                sending,
                Update.update(sending, new CommentMsg.Edited("Looks bad")).model());
        assertEquals(
                List.empty(), Update.update(sending, new CommentMsg.Confirmed()).cmds());
        assertEquals(sending, Update.update(sending, new CommentMsg.Cancelled()).model());
        assertEquals(
                List.empty(),
                Update.update(sending, new CommentMsg.EditorRequested()).cmds());
    }

    @Test
    void aBlankCommentIsNotSent() {
        Model blank = commenting(" \n ");

        Next next = Update.update(blank, new CommentMsg.Confirmed());

        assertEquals(List.empty(), next.cmds());
        assertEquals(blank, next.model());
    }

    @Test
    void cancellingDropsTheCommentAndGoesBackToThePullRequest() {
        Model cancelled = Update.update(commenting("half a thought"), new CommentMsg.Cancelled())
                .model();

        assertEquals(Option.of(SHOWN), cancelled.popup());
        assertEquals(
                Option.of(CommentPopup.on(SHOWN)),
                Update.update(cancelled, new CommentMsg.Requested()).model().popup());
    }

    @Test
    void aCommentSentFromTheCommentsGoesBackToThemReadsThemAgainAndHighlightsTheNewest() {
        CommentsPopup comments = CommentsPopup.loading(SHOWN)
                .read(Either.right(List.empty()))
                .withHighlighted(Option.of(new PullRequestComment.Id("IC_1")));
        Model commenting = loaded().openPopup(CommentPopup.on(comments).withText("Looks good"));
        Next sending = Update.update(commenting, new CommentMsg.Confirmed());
        AddComment cmd = (AddComment) sending.cmds().head();

        Next sent = Update.update(
                sending.model(), cmd.run(env(new StubGitProvider() {}, StubGitHub.empty(), Terminal.NONE)));

        assertEquals(
                Option.of(comments.withHighlighted(Option.none())), sent.model().popup());
        assertTrue(
                sent.cmds().contains(new LoadPullRequestDetails(42)),
                sent.cmds().toString());
        assertTrue(sent.cmds().contains(new LoadComments(42)), sent.cmds().toString());
    }

    @Test
    void aSentCommentGoesBackToThePullRequestAndReadsItsDetailsAgain() {
        Next sending = Update.update(commenting("Looks good"), new CommentMsg.Confirmed());
        AddComment cmd = (AddComment) sending.cmds().head();

        Next sent = Update.update(
                sending.model(), cmd.run(env(new StubGitProvider() {}, StubGitHub.empty(), Terminal.NONE)));

        assertEquals(Option.of(SHOWN), sent.model().popup());
        assertTrue(
                sent.cmds().contains(new LoadPullRequestDetails(42)),
                sent.cmds().toString());
        assertEquals(
                "gh pr comment 42 --body 'Looks good'",
                sent.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aCommentSentAfterThePopupClosedOnlyLogsIt() {
        AddComment cmd = new AddComment(FEATURE, 42, "Looks good");
        Model elsewhere = loaded();

        Next sent = Update.update(elsewhere, cmd.succeeded(List.empty()));

        assertEquals(Option.none(), sent.model().popup());
        assertTrue(
                !sent.cmds().contains(new LoadPullRequestDetails(42)),
                sent.cmds().toString());
    }

    @Test
    void aCommentGitHubRefusesKeepsThePopupWithTheCommentAndShowsWhy() {
        Next sending = Update.update(commenting("Looks good"), new CommentMsg.Confirmed());
        AddComment cmd = (AddComment) sending.cmds().head();
        StubGitHub refusing = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("could not add comment: network down"), 1),
                        List.of(ExecutedCommand.of("gh", "pr", "comment", "42", "--body", body))));
            }
        };

        Next failed = Update.update(sending.model(), cmd.run(env(new StubGitProvider() {}, refusing, Terminal.NONE)));

        assertEquals(
                Option.of(CommentPopup.on(SHOWN).withText("Looks good")),
                failed.model().popup());
        assertEquals(
                Option.of("Commenting on the pull request of feature failed: could not add comment: network down"),
                errorText(failed.model()));
        // It can be sent again.
        assertEquals(
                List.of(cmd),
                Update.update(failed.model(), new CommentMsg.Confirmed()).cmds());
    }

    @Test
    void theCommentIsOpenedInTheEditorWithTheTerminalHandedOver() {
        assertEquals(
                List.of(new EditComment("draft")),
                Update.update(commenting("draft"), new CommentMsg.EditorRequested())
                        .cmds());

        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(
                    String fileName, String text) {
                events.add("edit " + fileName + " " + text);
                return Either.right(Tuple.of("Looks good", List.of(NVIM)));
            }
        };

        assertEquals(
                new CommentMsg.EditorClosed("Looks good", List.of(NVIM)),
                new EditComment("draft").run(env(git, StubGitHub.empty(), terminal)));
        assertEquals(java.util.List.of("leave", "edit PR_COMMENT.md draft", "come back"), events);
    }

    @Test
    void theEditedCommentIsShownInThePopupToSend() {
        Next edited = Update.update(commenting("draft"), new CommentMsg.EditorClosed("Looks good", List.of(NVIM)));

        assertEquals(
                Option.of(CommentPopup.on(SHOWN).withText("Looks good")),
                edited.model().popup());
        assertEquals(List.empty(), edited.cmds());
        assertEquals(
                CommentCmd.EDIT_COMMENT,
                edited.model().outcome().commandLog().last().operation());
    }

    @Test
    void anEditorThatFailsKeepsTheCommentAndShowsWhy() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(
                    String fileName, String text) {
                return Either.left(new GitFailure<>(new GitError.EditorFailed("nvim", 1), List.of()));
            }
        };

        Model failed = Update.update(
                        commenting("draft"), new EditComment("draft").run(env(git, StubGitHub.empty(), Terminal.NONE)))
                .model();

        assertEquals(Option.of(CommentPopup.on(SHOWN).withText("draft")), failed.popup());
        AppError.OperationFailed error = assertInstanceOf(
                AppError.OperationFailed.class, failed.outcome().error().get());
        assertEquals(CommentCmd.EDIT_COMMENT, error.operation());
    }
}
