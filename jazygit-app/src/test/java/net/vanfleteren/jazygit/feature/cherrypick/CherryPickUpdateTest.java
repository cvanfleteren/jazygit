package net.vanfleteren.jazygit.feature.cherrypick;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Copying a commit and cherry-picking it onto another branch.
 */
class CherryPickUpdateTest {

    private static final Commit FIRST = TestModels.COMMITS.head();
    private static final Commit MERGE = new Commit(
            new Sha("mmmm"),
            "Ada",
            "ada@example.com",
            Instant.EPOCH,
            "Merge",
            "",
            List.of(new Sha("aaaa"), new Sha("ffff")));

    private static Model copied() {
        return Update.update(TestModels.loaded(), new CherryPickMsg.Copied(FIRST.sha()))
                .model();
    }

    /**
     * {@code model} once the branch feature is checked out.
     */
    private static Model onFeature(Model model) {
        Model branches = TestModels.localBranchesRead(
                        model,
                        List.of(
                                new Branch(new Branch.Name("main"), false, new Sha("aaaa")),
                                new Branch(new Branch.Name("feature"), true, new Sha("ffff"))))
                .model();
        return Update.update(branches, new LoadMsg.CommitsLoaded(TestModels.FEATURE_COMMITS))
                .model();
    }

    private static Model asked() {
        return Update.update(onFeature(copied()), new CherryPickMsg.Requested()).model();
    }

    @Test
    void copyingKeepsTheCommit() {
        assertEquals(Option.of(FIRST), copied().repoView().cherryPick());
    }

    @Test
    void copyingAnotherCommitReplacesTheCopy() {
        Model model = Update.update(
                        TestModels.loaded(),
                        new LoadMsg.CommitsLoaded(TestModels.FEATURE_COMMITS.appendAll(TestModels.COMMITS)))
                .model();
        model = Update.update(model, new CherryPickMsg.Copied(FIRST.sha())).model();

        Model next =
                Update.update(model, new CherryPickMsg.Copied(new Sha("ffff"))).model();

        assertEquals(
                Option.of(TestModels.FEATURE_COMMITS.head()), next.repoView().cherryPick());
    }

    @Test
    void aMergeCannotBeCopied() {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(MERGE)))
                .model();

        assertEquals(
                Option.none(),
                Update.update(model, new CherryPickMsg.Copied(MERGE.sha()))
                        .model()
                        .repoView()
                        .cherryPick());
    }

    @Test
    void droppingLetsGoOfTheCopy() {
        assertEquals(
                Option.none(),
                Update.update(copied(), new CherryPickMsg.Dropped())
                        .model()
                        .repoView()
                        .cherryPick());
    }

    @Test
    void theCopyStaysWhenAnotherBranchIsCheckedOut() {
        assertEquals(Option.of(FIRST), onFeature(copied()).repoView().cherryPick());
    }

    @Test
    void requestingAsksToPickTheCopyOntoTheCheckedOutBranch() {
        assertEquals(
                Option.of(new CherryPickPopup(FIRST, Option.of(new Branch.Name("feature")))),
                asked().popup(CherryPickPopup.class));
    }

    @Test
    void requestingWithoutACopyDoesNothing() {
        Model model = onFeature(TestModels.loaded());

        assertEquals(model, Update.update(model, new CherryPickMsg.Requested()).model());
    }

    @Test
    void aCommitTheBranchHasIsNotPickedAgain() {
        Next next = Update.update(copied(), new CherryPickMsg.Requested());

        assertEquals(Option.none(), next.model().popup(CherryPickPopup.class));
        assertEquals(Option.of("aaaa is already on the checked out branch"), errorText(next.model()));
    }

    @Test
    void confirmingPicksTheCopy() {
        Next next = Update.update(asked(), new CherryPickMsg.Confirmed());

        assertEquals(Option.none(), next.model().popup(CherryPickPopup.class));
        assertEquals(new CherryPickCmd(FIRST.sha()), next.cmds().head());
    }

    @Test
    void cancellingClosesThePopupAndKeepsTheCopy() {
        Next next = Update.update(asked(), new CherryPickMsg.Cancelled());

        assertEquals(Option.none(), next.model().popup(CherryPickPopup.class));
        assertEquals(Option.of(FIRST), next.model().repoView().cherryPick());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void pickingLetsGoOfTheCopy() {
        Model running = Update.update(asked(), new CherryPickMsg.Confirmed()).model();
        CherryPickCmd cmd = new CherryPickCmd(FIRST.sha());

        Model next = Update.update(running, cmd.succeeded(StubGitProvider.executed("git cherry-pick aaaa")))
                .model();

        assertEquals(Option.none(), next.repoView().cherryPick());
    }

    @Test
    void aFailureShowsTheErrorAndKeepsTheCopy() {
        Model running = Update.update(asked(), new CherryPickMsg.Confirmed()).model();

        Model next = Update.update(running, failed(new CherryPickCmd(FIRST.sha()), "conflict"))
                .model();

        assertEquals(Option.of("Cherry-pick of aaaa failed: conflict"), errorText(next));
        assertEquals(Option.of(FIRST), next.repoView().cherryPick());
    }
}
