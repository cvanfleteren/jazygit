package net.vanfleteren.jazygit.kernel.state.view;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Remark;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView.Listed;
import org.junit.jupiter.api.Test;

class CommitFilesViewTest {

    private static final Changes PULL_REQUEST = new Changes.OfPullRequest(new PullRequestCommits(
            new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
    private static final CommitFile OPEN = new CommitFile("src/Open.java", ChangeType.MODIFIED);
    private static final CommitFile RESOLVED = new CommitFile("src/Resolved.java", ChangeType.MODIFIED);
    private static final CommitFile RENAMED = new CommitFile("New.java", ChangeType.RENAMED, Option.of("Old.java"));
    private static final CommitFile QUIET = new CommitFile("Quiet.java", ChangeType.ADDED);
    private static final List<CommitFile> FILES = List.of(OPEN, RESOLVED, RENAMED, QUIET);

    @Test
    void theFilesListedAreThoseWithThreadsOfTheKindAskedFor() {
        CommitFilesView view = read();
        Option<StatedRemarks> stated = Option.of(StatedRemarks.of(List.of(
                thread("src/Open.java", false),
                thread("src/Open.java", true),
                thread("src/Resolved.java", true),
                // On the old path of the renamed file.
                thread("Old.java", false))));

        assertEquals(new Loadable.Loaded<>(FILES), view.listedFiles(stated));
        assertEquals(
                new Loadable.Loaded<>(List.of(OPEN, RENAMED)),
                view.withListed(Listed.UNRESOLVED).listedFiles(stated));
        assertEquals(
                new Loadable.Loaded<>(List.of(OPEN, RESOLVED, RENAMED)),
                view.withListed(Listed.REMARKED).listedFiles(stated));
    }

    @Test
    void untilTheThreadsAreReadNoneIsListedButAllAre() {
        CommitFilesView loading = read();
        Option<StatedRemarks> reading = Option.of(remarks(Loadable.loading()));
        assertEquals(new Loadable.Loaded<>(FILES), loading.listedFiles(reading));
        assertEquals(
                new Loadable.Loading<>(), loading.withListed(Listed.UNRESOLVED).listedFiles(reading));

        Failure failure = new Failure.Bug(new Exception());
        assertEquals(
                new Loadable.Failed<>(failure),
                loading.withListed(Listed.REMARKED).listedFiles(Option.of(remarks(new Loadable.Failed<>(failure)))));
        assertEquals(
                new Loadable.Loading<>(),
                read().withFiles(Loadable.loading())
                        .withListed(Listed.UNRESOLVED)
                        .listedFiles(Option.of(StatedRemarks.of(List.empty()))));
    }

    @Test
    void withoutRemarksStatedNoneIsListedButAllAre() {
        assertEquals(new Loadable.Loaded<>(FILES), read().listedFiles(Option.none()));
        assertEquals(
                new Loadable.Loaded<>(List.empty()),
                read().withListed(Listed.REMARKED).listedFiles(Option.none()));
    }

    @Test
    void cyclesThroughWhatItLists() {
        assertEquals(Listed.UNRESOLVED, Listed.ALL.next());
        assertEquals(Listed.REMARKED, Listed.UNRESOLVED.next());
        assertEquals(Listed.ALL, Listed.REMARKED.next());
    }

    private static CommitFilesView read() {
        return CommitFilesView.of(PULL_REQUEST, new PaneId("branches"), new PaneId("files"))
                .withFiles(new Loadable.Loaded<>(FILES));
    }

    private static StatedRemarks remarks(Loadable<List<RemarkThread>> threads) {
        return new StatedRemarks(threads, true, HashSet.empty(), HashSet.empty(), HashMap.empty(), Option.none());
    }

    private static RemarkThread thread(String path, boolean resolved) {
        return new RemarkThread(
                new RemarkThread.Id("T"),
                path,
                Option.none(),
                RemarkThread.Subject.LINE,
                Option.of(1),
                DiffSide.NEW,
                Option.none(),
                Option.none(),
                Option.of(1),
                false,
                resolved,
                0,
                List.of(new Remark(new Remark.Id("C"), "bob", Instant.EPOCH, "Hm", Option.none())));
    }
}
