package net.vanfleteren.jazygit.features.github.comments;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.CommentRights;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.core.gh.model.PullRequestComment.Kind;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.github.browse.OpenLinkCmd;
import net.vanfleteren.jazygit.features.github.comment.CommentMsg;
import net.vanfleteren.jazygit.features.github.comment.CommentPopup;
import net.vanfleteren.jazygit.features.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.features.github.compose.ComposeMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.lib.image.Picture;
import org.junit.jupiter.api.Test;

/**
 * Reading the conversation on the pull request that is shown: opening it, moving through it and going back.
 */
class CommentsUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new ForgeBranch("feature"), false, Checks.PASSING, Review.APPROVED);
    private static final PullRequestDetails DETAILS =
            StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
    private static final PullRequestPopup SHOWN =
            PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0).with(Either.right(DETAILS), 0);
    private static final PullRequestComment DESCRIPTION =
            comment(PullRequestComment.DESCRIPTION.value(), Kind.DESCRIPTION, "Adds it");
    private static final PullRequestComment FIRST = comment("IC_1", Kind.COMMENT, "Nice");
    private static final PullRequestComment APPROVAL = comment("PRR_1", Kind.APPROVED, "");
    private static final List<PullRequestComment> CONVERSATION = List.of(DESCRIPTION, FIRST, APPROVAL);

    private static PullRequestComment comment(String id, Kind kind, String body) {
        return new PullRequestComment(
                new PullRequestComment.Id(id),
                kind,
                "bob",
                Instant.parse("2026-10-10T08:00:00Z"),
                body,
                false,
                Option.none(),
                CommentRights.ALL);
    }

    private static Model reading(List<PullRequestComment> comments) {
        return loaded().openPopup(CommentsPopup.loading(SHOWN).read(Either.right(comments)));
    }

    @Test
    void viewingTheCommentsOpensThemAndReadsThem() {
        Next next = Update.update(loaded().openPopup(SHOWN), new CommentsMsg.Requested());

        assertEquals(Option.of(CommentsPopup.loading(SHOWN)), next.model().popup());
        assertEquals(List.of(new LoadComments(new PrNumber(42))), next.cmds());
    }

    @Test
    void theCommentsAreOnlyShownOnceTheDetailsAreRead() {
        Model loading = loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0));
        Model failed = loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0)
                .with(Either.left(refused("offline")), 0));

        assertEquals(Next.of(loading), Update.update(loading, new CommentsMsg.Requested()));
        assertEquals(Next.of(failed), Update.update(failed, new CommentsMsg.Requested()));
    }

    @Test
    void theCommentsReadOpenOnTheNewest() {
        Model read = Update.update(
                        loaded().openPopup(CommentsPopup.loading(SHOWN)),
                        new CommentsMsg.Loaded(new PrNumber(42), CONVERSATION))
                .model();

        CommentsPopup popup = read.popup(CommentsPopup.class).get();
        assertEquals(CONVERSATION, popup.all());
        assertEquals(Option.of(APPROVAL), popup.highlightedComment());
    }

    @Test
    void movingHighlightsAComment() {
        Model moved = Update.update(reading(CONVERSATION), new CommentsMsg.Highlighted(FIRST.id()))
                .model();

        assertEquals(Option.of(FIRST), moved.popup(CommentsPopup.class).flatMap(CommentsPopup::highlightedComment));
        // A comment that is not there is not highlighted.
        assertEquals(
                moved,
                Update.update(moved, new CommentsMsg.Highlighted(new PullRequestComment.Id("gone")))
                        .model());
    }

    @Test
    void readAgainTheHighlightStaysOnItsCommentWhileItIsThere() {
        Model moved = Update.update(reading(CONVERSATION), new CommentsMsg.Highlighted(FIRST.id()))
                .model();
        PullRequestComment newer = comment("IC_2", Kind.COMMENT, "Thanks");

        Model again = Update.update(moved, new CommentsMsg.Loaded(new PrNumber(42), CONVERSATION.append(newer)))
                .model();
        Model without = Update.update(moved, new CommentsMsg.Loaded(new PrNumber(42), List.of(DESCRIPTION, APPROVAL)))
                .model();

        assertEquals(Option.of(FIRST), again.popup(CommentsPopup.class).flatMap(CommentsPopup::highlightedComment));
        assertEquals(
                Option.of(APPROVAL), without.popup(CommentsPopup.class).flatMap(CommentsPopup::highlightedComment));
    }

    @Test
    void readingTheCommentsCanFail() {
        Model failed = Update.update(
                        loaded().openPopup(CommentsPopup.loading(SHOWN)),
                        new CommentsMsg.LoadFailed(new PrNumber(42), refused("offline")))
                .model();

        CommentsPopup popup = failed.popup(CommentsPopup.class).get();
        assertTrue(popup.comments().get().isLeft());
        assertEquals(Option.none(), popup.highlightedComment());
    }

    @Test
    void commentsReadAfterGoingBackChangeNothing() {
        Model back =
                Update.update(reading(CONVERSATION), new CommentsMsg.Closed()).model();

        assertEquals(Option.of(SHOWN), back.popup());
        assertEquals(
                back,
                Update.update(back, new CommentsMsg.Loaded(new PrNumber(42), List.empty()))
                        .model());
        // Nor do those of another pull request.
        Model loading = loaded().openPopup(CommentsPopup.loading(SHOWN));
        assertEquals(
                loading,
                Update.update(loading, new CommentsMsg.Loaded(new PrNumber(7), CONVERSATION))
                        .model());
    }

    @Test
    void openingTheCommentsOpensThePullRequestInTheBrowserAndStays() {
        Model reading = reading(CONVERSATION);

        Next next = Update.update(reading, new CommentsMsg.OpenChosen());

        assertEquals(List.of(new OpenPullRequest(FEATURE, new PrNumber(42))), next.cmds());
        assertEquals(reading.popup(), next.model().popup());
    }

    @Test
    void aLinkChosenInTheCommentsOpensInTheBrowserAndTheyStay() {
        Model reading = reading(CONVERSATION);

        Next next = Update.update(reading, new CommentsMsg.LinkChosen("https://example.com"));

        assertEquals(List.of(new OpenLinkCmd("https://example.com")), next.cmds());
        assertEquals(reading.popup(), next.model().popup());
    }

    @Test
    void commentingFromTheCommentsGoesBackToThem() {
        Model reading = Update.update(reading(CONVERSATION), new CommentsMsg.Highlighted(FIRST.id()))
                .model();
        CommentsPopup comments = reading.popup(CommentsPopup.class).get();

        Model commenting = Update.update(reading, new CommentMsg.Requested()).model();
        Model cancelled = Update.update(commenting, new ComposeMsg.Cancelled()).model();

        assertEquals(Option.of(CommentPopup.on(comments)), commenting.popup());
        assertEquals(Option.of(comments), cancelled.popup());
    }

    @Test
    void whatChangesThePullRequestChangesTheDialogTheCommentsGoBackTo() {
        PullRequestPopup loading = PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0);
        Model reading = loaded().openPopup(CommentsPopup.loading(loading));
        Model commenting = loaded().openPopup(CommentPopup.on(CommentsPopup.loading(loading)));

        Model read = Update.update(reading, new PullRequestMsg.DetailsLoaded(new PrNumber(42), 0, DETAILS))
                .model();
        Model readWhileCommenting = Update.update(
                        commenting, new PullRequestMsg.DetailsLoaded(new PrNumber(42), 0, DETAILS))
                .model();

        assertEquals(Option.of(SHOWN), read.popup(CommentsPopup.class).map(CommentsPopup::shown));
        assertEquals(
                Option.of(SHOWN), readWhileCommenting.popup(CommentPopup.class).map(CommentPopup::pullRequestPopup));
    }

    @Test
    void commentsReadWhileCommentingShowOnceBack() {
        Model commenting = loaded().openPopup(CommentPopup.on(CommentsPopup.loading(SHOWN)));

        Model read = Update.update(commenting, new CommentsMsg.Loaded(new PrNumber(42), CONVERSATION))
                .model();

        assertEquals(
                Option.of(CONVERSATION),
                read.popup(CommentPopup.class).map(CommentPopup::from).map(from -> ((CommentsPopup) from).all()));
    }

    @Test
    void theImagesOfTheConversationReadAreReadToBeShown() {
        String pasted = "https://github.com/user-attachments/assets/5a779fb0";
        String icon = "https://avatars.githubusercontent.com/u/1";
        List<PullRequestComment> withImages = List.of(
                comment(PullRequestComment.DESCRIPTION.value(), Kind.DESCRIPTION, "![shot](" + pasted + ")"),
                comment(
                        "IC_1",
                        Kind.COMMENT,
                        "<img width=\"1854\" alt=\"image\" src=\"" + pasted + "\" />\n\n" + "<img src=\"" + icon
                                + "\" width=\"14\"> and ![x](https://example.com/t.png)"));

        Next read = Update.update(
                loaded().openPopup(CommentsPopup.loading(SHOWN)), new CommentsMsg.Loaded(new PrNumber(42), withImages));

        // Each once, and not one elsewhere, which would tell its server who reads the comment.
        assertEquals(
                List.of(
                        new CommentsCmd.LoadPicture(new PrNumber(42), pasted),
                        new CommentsCmd.LoadPicture(new PrNumber(42), icon)),
                read.cmds());
        assertEquals(
                HashMap.of(pasted, Option.none(), icon, Option.none()),
                read.model().popup(CommentsPopup.class).get().pictures());
        // Read again, they are not read again.
        assertEquals(
                List.empty(),
                Update.update(read.model(), new CommentsMsg.Loaded(new PrNumber(42), withImages))
                        .cmds());

        Picture picture = new Picture(1, 2, Vector.of(0xffff0000, 0xff00ff00));
        Model shown = Update.update(
                        read.model(), new CommentsMsg.PictureLoaded(new PrNumber(42), pasted, Option.of(picture)))
                .model();
        assertEquals(
                HashMap.of(pasted, picture),
                shown.popup(CommentsPopup.class).get().shownPictures());
        // Kept when the conversation is read again.
        assertEquals(
                HashMap.of(pasted, picture),
                Update.update(shown, new CommentsMsg.Loaded(new PrNumber(42), withImages))
                        .model()
                        .popup(CommentsPopup.class)
                        .get()
                        .shownPictures());
    }

    @Test
    void atMostTwentyImagesOfAConversationAreRead() {
        List<PullRequestComment> many = List.range(0, 25)
                .map(n ->
                        comment("IC_" + n, Kind.COMMENT, "![](https://github.com/user-attachments/assets/" + n + ")"));

        Next read = Update.update(
                loaded().openPopup(CommentsPopup.loading(SHOWN)), new CommentsMsg.Loaded(new PrNumber(42), many));

        assertEquals(20, read.cmds().size());
        assertEquals(
                List.empty(),
                Update.update(read.model(), new CommentsMsg.Loaded(new PrNumber(42), many))
                        .cmds());
    }

    @Test
    void anImageReadWhileCommentingShowsOnceBack() {
        String pasted = "https://github.com/user-attachments/assets/5a779fb0";
        Picture picture = new Picture(1, 2, Vector.of(0xffff0000, 0xff00ff00));
        Model commenting = Update.update(
                        reading(List.of(comment("IC_1", Kind.COMMENT, "![](" + pasted + ")"))),
                        new CommentMsg.Requested())
                .model();

        Model read = Update.update(
                        commenting, new CommentsMsg.PictureLoaded(new PrNumber(42), pasted, Option.of(picture)))
                .model();

        assertEquals(
                Option.of(HashMap.of(pasted, picture)),
                read.popup(CommentPopup.class)
                        .map(CommentPopup::from)
                        .map(from -> ((CommentsPopup) from).shownPictures()));
    }
}
