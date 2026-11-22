package net.vanfleteren.jazygit.features.github.review;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.loadedWithTheFeature;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.CommentRights;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.Reactions;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Folding;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.github.remarks.FromGitHub;
import net.vanfleteren.jazygit.features.github.remarks.PrFiles;
import net.vanfleteren.jazygit.features.github.remarks.RemarksMsg;
import net.vanfleteren.jazygit.features.github.remarks.RemarksState;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.diff.Remark;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The pull request, its files, threads and cursors the tests of commenting on the lines of its diff share.
 */
final class ReviewFixtures {

    private ReviewFixtures() {}

    static final String HEAD = "a".repeat(40);
    static final String BASE = "b".repeat(40);
    static final PullRequestCommits COMMITS = new PullRequestCommits(7, "owner/repo", "", new Sha(BASE), new Sha(HEAD));
    static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    static final AreaId AREA = new AreaId("commit-file-changes");
    // Line 2 of a.txt changed, in a first hunk; line 10 in a second.
    static final DiffText DIFF = DiffText.of("""
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            @@ -10,1 +10,1 @@
            -ten
            +TEN
            """);
    static final ScrolledContent CONTENT =
            new ScrolledContent.FileChanges(new Changes.OfPullRequest(COMMITS), List.of(A), DIFF);
    static final ReviewThread OPEN = thread("T1", false, ReviewThread.Rights.ALL);
    static final ReviewThread SETTLED = thread("T2", true, new ReviewThread.Rights(true, true, false));

    static ReviewThread thread(String id, boolean resolved, ReviewThread.Rights rights) {
        return new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(2),
                Option.none(),
                Side.RIGHT,
                Option.of(2),
                resolved,
                false,
                ReviewThread.Subject.LINE,
                List.of(
                        new Comment(
                                "bob",
                                Instant.EPOCH,
                                "Why?\nReally?",
                                Option.none(),
                                new ReviewThread.Comment.Id("C" + id.substring(1)),
                                new ReviewThread.Comment.RestId("1"),
                                CommentRights.ALL),
                        // A reply, R1 on T1.
                        new Comment(
                                "carol",
                                Instant.EPOCH,
                                "Yes",
                                Option.none(),
                                new ReviewThread.Comment.Id("R" + id.substring(1)),
                                new ReviewThread.Comment.RestId("2"),
                                CommentRights.ALL)),
                0,
                new ReviewThread.Id(id),
                rights);
    }

    static Anchor.OnLine line(Side side, int number) {
        return new Anchor.OnLine("a.txt", FromGitHub.side(side), number);
    }

    static Model files(PullRequestDetails.State state, String head) {
        return files(state, head, List.of(OPEN, SETTLED));
    }

    static Model files(PullRequestDetails.State state, String head, List<ReviewThread> threads) {
        Model opened = Update.update(
                        loadedWithTheFeature(),
                        new CommitFilesMsg.Opened(new Changes.OfPullRequest(COMMITS), Scopes.BRANCHES.pane()))
                .model();
        Model read = Update.update(
                        opened,
                        new RemarksMsg.Loaded(
                                new PrNumber(7),
                                new ReviewRemarks(state, Sha.parse(head), Sha.parse(BASE), threads),
                                HashSet.empty(),
                                0))
                .model();
        return read.mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(files.commitFiles()
                .map(view -> view.withDiff(Option.of(new Preview<>(List.of(A), new Loadable.Loaded<>(DIFF))))))));
    }

    static Model files() {
        return files(PullRequestDetails.State.OPEN, HEAD);
    }

    static DiffCursorAt at(Anchor cursor, Option<Anchor> mark) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 3, 0, Option.of(cursor), mark),
                cursor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    static DiffCursorAt at(Anchor cursor) {
        return at(cursor, Option.none());
    }

    static Anchor onThread(String id) {
        return new Anchor.Thread(new RemarkThread.Id(id), Option.of(line(Side.RIGHT, 2)));
    }

    static PrFiles view(Model model) {
        return RemarksState.files(model).get();
    }

    static Option<AppError> error(Model model) {
        return model.outcome().error();
    }

    static ReviewRemarks remarks() {
        return new ReviewRemarks(
                PullRequestDetails.State.OPEN, Sha.parse(HEAD), Sha.parse(BASE), List.of(OPEN, SETTLED));
    }

    static RemarksMsg.Loaded readAgain(Set<ReviewThread.Id> settled, ReviewThread... threads) {
        return new RemarksMsg.Loaded(
                new PrNumber(7),
                new ReviewRemarks(PullRequestDetails.State.OPEN, Sha.parse(HEAD), Sha.parse(BASE), List.of(threads)),
                settled,
                0);
    }

    /**
     * {@code model} with a.txt read whole, 30 lines with lines 2 and 10 changed as {@link #DIFF} has them, and
     * {@code lines} expanded in it, in the area of the diff of the files.
     */
    static Model expanded(Model model, Folding.Lines lines) {
        DiffText whole = DiffText.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,30 +1,30 @@\n"
                + List.rangeClosed(1, 30)
                        .map(n -> switch (n) {
                            case 1 -> " one\n";
                            case 2 -> "-two\n+TWO\n";
                            case 3 -> " three\n";
                            case 10 -> "-ten\n+TEN\n";
                            default -> " line" + n + "\n";
                        })
                        .mkString());
        ReadWholeFile read = new ReadWholeFile(
                AREA,
                CONTENT,
                new WholeFile.Source.OfFiles(new Changes.OfPullRequest(COMMITS), A),
                "a.txt",
                model.preferences().diffOptions());
        Expansion expansion = new Expansion(
                CONTENT,
                io.vavr.collection.HashMap.of(
                        "a.txt", new Expansion.Expanded(read.file(), new Loadable.Loaded<>(whole), List.of(lines))));
        return model.mapRepoView(view -> view.withExpansion(AREA, expansion));
    }

    static Anchor onComment(String comment, String thread) {
        return new Anchor.Remark(new Remark.Id(comment), (Anchor.Thread) onThread(thread));
    }

    static List<String> comments(Model model, String thread) {
        return view(model)
                .threads()
                .find(each -> each.id().equals(new ReviewThread.Id(thread)))
                .map(each -> each.comments().map(comment -> comment.id().value()))
                .getOrElse(List.empty());
    }

    /**
     * {@code model} once the comment {@code comment} of {@code thread} was asked to be deleted, and that confirmed.
     */
    static Next deleting(Model model, Anchor on) {
        Model asked =
                Update.update(model, new DeleteCommentMsg.DeleteAsked(at(on))).model();
        return Update.update(asked, new DeleteCommentMsg.DeleteConfirmed());
    }

    /**
     * {@code model} with each comment of its threads giving the viewer {@code rights}.
     */
    static Model withRights(Model model, CommentRights rights) {
        return RemarksState.mapOpen(
                model,
                view -> view.withRemarks(view.remarks()
                        .map(read -> new ReviewRemarks(
                                read.state(),
                                read.head(),
                                read.base(),
                                read.threads()
                                        .map(thread -> new ReviewThread(
                                                thread.path(),
                                                thread.side(),
                                                thread.line(),
                                                thread.startLine(),
                                                thread.startSide(),
                                                thread.originalLine(),
                                                thread.resolved(),
                                                thread.outdated(),
                                                thread.subject(),
                                                thread.comments()
                                                        .map(comment -> new Comment(
                                                                comment.author(),
                                                                comment.at(),
                                                                comment.body(),
                                                                comment.hidden(),
                                                                comment.id(),
                                                                comment.restId(),
                                                                rights)),
                                                thread.more(),
                                                thread.id(),
                                                thread.rights()))))));
    }

    /**
     * {@code thread} with only its first comment read, and {@code more} that GitHub has besides.
     */
    static ReviewThread firstOnly(ReviewThread thread, int more) {
        return withMore(thread.withComments(thread.comments().take(1)), more);
    }

    /**
     * {@code thread} with {@code more} comments that GitHub has besides those read.
     */
    static ReviewThread withMore(ReviewThread thread, int more) {
        return new ReviewThread(
                thread.path(),
                thread.side(),
                thread.line(),
                thread.startLine(),
                thread.startSide(),
                thread.originalLine(),
                thread.resolved(),
                thread.outdated(),
                thread.subject(),
                thread.comments(),
                more,
                thread.id(),
                thread.rights());
    }

    static Reactions reactions(Model model, String comment) {
        return view(model)
                .threads()
                .flatMap(ReviewThread::comments)
                .find(each -> each.id().equals(new Comment.Id(comment)))
                .map(Comment::reactions)
                .get();
    }
}
