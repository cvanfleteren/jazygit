package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Searching the log of HEAD: typing the query, running it, and listing what it finds until it is cleared.
 */
class CommitSearchUpdateTest {

    private static final List<Commit> FOUND =
            List.of(new Commit(new Sha("0123"), "Bob", "bob@example.com", Instant.EPOCH, "Fix it", ""));

    private static Model typed(Model model, String text) {
        return Update.update(
                        Update.update(model, new CommitSearchMsg.Opened()).model(), new CommitSearchMsg.Edited(text))
                .model();
    }

    private static Next run(String text) {
        return Update.update(typed(loaded(), text), new CommitSearchMsg.Submitted());
    }

    @Test
    void openingTheLineStartsItEmptyAndEditingChangesIt() {
        Model opened = Update.update(loaded(), new CommitSearchMsg.Opened()).model();
        assertEquals(Option.of(""), opened.repoView().searchLine());

        assertEquals(Option.of("fix"), typed(loaded(), "fix").repoView().searchLine());
    }

    @Test
    void editingDoesNothingWhileTheLineIsClosed() {
        Model model = loaded();

        assertEquals(
                model, Update.update(model, new CommitSearchMsg.Edited("fix")).model());
    }

    @Test
    void runningTheQueryClosesTheLineAndReadsWhatItFinds() {
        Next next = run(" fix author:bob ");

        assertEquals(Option.none(), next.model().repoView().searchLine());
        assertEquals(
                Option.of(CommitSearch.loading("fix author:bob", Option.none())),
                next.model().repoView().commitSearch());
        assertEquals(List.of(new Cmd.SearchCommits(CommitQuery.parse("fix author:bob"), Option.none())), next.cmds());
    }

    @Test
    void reopeningTheLineShowsTheQueryThatRan() {
        Model searched = run("fix").model();

        assertEquals(
                Option.of("fix"),
                Update.update(searched, new CommitSearchMsg.Opened())
                        .model()
                        .repoView()
                        .searchLine());
    }

    @Test
    void cancellingClosesTheLineAndKeepsTheSearch() {
        Model searched = run("fix").model();

        Model cancelled = Update.update(typed(searched, "other"), new CommitSearchMsg.Cancelled())
                .model();

        assertEquals(Option.none(), cancelled.repoView().searchLine());
        assertEquals(searched.repoView().commitSearch(), cancelled.repoView().commitSearch());
    }

    @Test
    void runningAQueryThatFiltersNothingClearsTheSearch() {
        Model searched = run("fix").model();

        Next next = Update.update(typed(searched, "author: "), new CommitSearchMsg.Submitted());

        assertEquals(Option.none(), next.model().repoView().commitSearch());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void clearingShowsTheLogAgain() {
        Model searched = run("fix").model();

        Model cleared = Update.update(searched, new CommitSearchMsg.Cleared()).model();

        assertEquals(Option.none(), cleared.repoView().commitSearch());
        assertEquals(Option.none(), cleared.repoView().searchLine());
    }

    @Test
    void theResultsOfTheQueryThatRanAreShownAndTheCommitsFoundCanBeActedOn() {
        Model searched = run("fix").model();

        Model found = Update.update(
                        searched, new LoadMsg.CommitSearchLoaded(CommitQuery.parse("fix"), Option.none(), FOUND))
                .model();

        assertEquals(new Loaded<>(FOUND), found.repoView().commitSearch().get().results());
        assertEquals(FOUND.headOption(), found.commit(new Sha("0123")));
        assertEquals(COMMITS.headOption(), found.commit(new Sha("aaaa")));
    }

    @Test
    void theResultsOfAnotherQueryAreDropped() {
        Model searched = run("fix").model();

        Model after = Update.update(
                        searched, new LoadMsg.CommitSearchLoaded(CommitQuery.parse("other"), Option.none(), FOUND))
                .model();

        assertEquals(searched.repoView(), after.repoView());
    }

    @Test
    void aFailedSearchIsShownAndReadAgainOnTheNextRefresh() {
        Model searched = run("path:x").model();
        Cmd.SearchCommits load = new Cmd.SearchCommits(CommitQuery.parse("path:x"), Option.none());

        Model failed =
                Update.update(searched, load.failed(refused("bad pathspec"))).model();

        assertInstanceOf(Failed.class, failed.repoView().commitSearch().get().results());
        assertTrue(Update.refresh(failed).cmds().contains(load));
    }

    @Test
    void theSearchRunsAgainWhenTheLogChanges() {
        Model searched = run("fix").model();

        Next next = Update.update(searched, new LoadMsg.CommitsLoaded(FOUND));

        assertTrue(next.cmds().contains(new Cmd.SearchCommits(CommitQuery.parse("fix"), Option.none())));
        assertFalse(Update.update(loaded(), new LoadMsg.CommitsLoaded(FOUND))
                .cmds()
                .exists(Cmd.SearchCommits.class::isInstance));
    }

    @Test
    void whileOnlyTheCommitsNotOnTheBaseAreListedTheSearchLeavesOutTheBase() {
        BranchRef base = new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("main")));
        Ahead ahead = new Ahead(base, FOUND, false, Option.none());
        Model model = Update.update(loaded(), new LoadMsg.AheadLoaded(AheadUpdate.ofHead(loaded()), Option.of(ahead)))
                .model();
        Model searched = Update.update(typed(model, "fix"), new CommitSearchMsg.Submitted())
                .model();

        Next only = Update.update(searched, new AheadMsg.Toggled());

        assertTrue(only.model().repoView().onlyAhead());
        assertEquals(List.of(new Cmd.SearchCommits(CommitQuery.parse("fix"), Option.of(base))), only.cmds());
        Next whole = Update.update(only.model(), new AheadMsg.Toggled());
        assertEquals(List.of(new Cmd.SearchCommits(CommitQuery.parse("fix"), Option.none())), whole.cmds());
    }
}
