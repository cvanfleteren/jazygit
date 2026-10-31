package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.CommitFilesView.Listed;
import org.junit.jupiter.api.Test;

class CommitFilesViewTest {

    private static final CommitFilesView.Changes PULL_REQUEST = new CommitFilesView.Changes.OfPullRequest(
            new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
    private static final CommitFile OPEN = new CommitFile("src/Open.java", ChangeType.MODIFIED);
    private static final CommitFile RESOLVED = new CommitFile("src/Resolved.java", ChangeType.MODIFIED);
    private static final CommitFile RENAMED = new CommitFile("New.java", ChangeType.RENAMED, Option.of("Old.java"));
    private static final CommitFile QUIET = new CommitFile("Quiet.java", ChangeType.ADDED);
    private static final List<CommitFile> FILES = List.of(OPEN, RESOLVED, RENAMED, QUIET);

    @Test
    void theFilesListedAreThoseWithThreadsOfTheKindAskedFor() {
        CommitFilesView view = read(List.of(
                thread("src/Open.java", false),
                thread("src/Open.java", true),
                thread("src/Resolved.java", true),
                // On the old path of the renamed file.
                thread("Old.java", false)));

        assertEquals(new Loadable.Loaded<>(FILES), view.listedFiles());
        assertEquals(
                new Loadable.Loaded<>(List.of(OPEN, RENAMED)),
                view.withListed(Listed.UNRESOLVED).listedFiles());
        assertEquals(
                new Loadable.Loaded<>(List.of(OPEN, RESOLVED, RENAMED)),
                view.withListed(Listed.REMARKED).listedFiles());
    }

    @Test
    void untilTheThreadsAreReadNoneIsListedButAllAre() {
        CommitFilesView loading =
                CommitFilesView.of(PULL_REQUEST, "branches", "files").withFiles(new Loadable.Loaded<>(FILES));
        assertEquals(new Loadable.Loaded<>(FILES), loading.listedFiles());
        assertEquals(
                new Loadable.Loading<>(), loading.withListed(Listed.UNRESOLVED).listedFiles());

        Failure failure = new Failure.Bug(new Exception());
        assertEquals(
                new Loadable.Failed<>(failure),
                loading.withRemarks(Option.of(new Loadable.Failed<>(failure)))
                        .withListed(Listed.REMARKED)
                        .listedFiles());
        assertEquals(
                new Loadable.Loading<>(),
                read(List.empty())
                        .withFiles(Loadable.loading())
                        .withListed(Listed.UNRESOLVED)
                        .listedFiles());
    }

    @Test
    void cyclesThroughWhatItLists() {
        assertEquals(Listed.UNRESOLVED, Listed.ALL.next());
        assertEquals(Listed.REMARKED, Listed.UNRESOLVED.next());
        assertEquals(Listed.ALL, Listed.REMARKED.next());
    }

    @Test
    void aDeletedCommentLeavesItsThreadAndTheThreadGoesWithItsLastUnlessGitHubHasMore() {
        ReviewThread read = thread("src/Open.java", false);
        ReviewThread more = new ReviewThread(
                read.path(),
                read.side(),
                read.line(),
                read.startLine(),
                read.startSide(),
                read.originalLine(),
                false,
                false,
                read.subject(),
                List.of(new ReviewThread.Comment(
                        "bob", Instant.EPOCH, "Hm", Option.none(), "M", "2", CommentRights.ALL)),
                3,
                "TM",
                ReviewThread.Rights.ALL);
        CommitFilesView view = read(List.of(read, more));

        CommitFilesView deleted = view.withoutComment("C").withoutComment("M");

        assertEquals(List.of("TM"), deleted.threads().map(ReviewThread::id));
        assertEquals(List.empty(), deleted.threads().head().comments());
        assertEquals(2, deleted.version());
        // One that is gone already changes nothing, not even the version.
        assertEquals(deleted, deleted.withoutComment("C"));
    }

    @Test
    void aCommentEditedSaysWhatGitHubHoldsAtANewerVersion() {
        CommitFilesView view = read(List.of(thread("a.txt", false)));

        CommitFilesView edited = view.withEdited("C", "Hm?");

        assertEquals(List.of("Hm?"), edited.threads().head().comments().map(ReviewThread.Comment::body));
        assertEquals(1, edited.version());
        // One that is gone changes nothing, not even the version.
        assertEquals(edited, edited.withEdited("X", "Hm!"));
    }

    private static CommitFilesView read(List<ReviewThread> threads) {
        return CommitFilesView.of(PULL_REQUEST, "branches", "files")
                .withFiles(new Loadable.Loaded<>(FILES))
                .withRemarks(Option.of(new Loadable.Loaded<>(
                        new ReviewRemarks(PullRequestDetails.State.OPEN, Option.none(), Option.none(), threads))));
    }

    private static ReviewThread thread(String path, boolean resolved) {
        return new ReviewThread(
                path,
                ReviewThread.Side.RIGHT,
                Option.of(1),
                Option.none(),
                ReviewThread.Side.RIGHT,
                Option.of(1),
                resolved,
                false,
                ReviewThread.Subject.LINE,
                List.of(new ReviewThread.Comment(
                        "bob", Instant.EPOCH, "Hm", Option.none(), "C", "1", CommentRights.ALL)),
                0,
                "T",
                ReviewThread.Rights.ALL);
    }
}
