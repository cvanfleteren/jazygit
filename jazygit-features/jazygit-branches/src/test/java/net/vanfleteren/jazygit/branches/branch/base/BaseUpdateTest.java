package net.vanfleteren.jazygit.branches.branch.base;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.localBranchesRead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.branches.branch.BranchError;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BaseCandidate;
import net.vanfleteren.jazygit.git.model.BaseCandidates;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Setting the branch a branch is built on (ADR 143).
 */
class BaseUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName STAGING = new BranchName("staging/sync");
    private static final BranchName SIBLING = new BranchName("sibling");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final Sha FEATURE_TIP = new Sha("ffff");

    private static final BaseCandidate MAIN_BASE = candidate(MAIN, 32, 0);
    private static final BaseCandidate STAGING_BASE = candidate(STAGING, 26, 0);
    private static final BaseCandidate SIBLING_BASE = candidate(SIBLING, 26, 7);

    private static BaseCandidate candidate(BranchName name, int own, int theirs) {
        return new BaseCandidate(name, new BranchRef.Local(name), own, theirs);
    }

    /**
     * The model with main checked out, the default branch, and feature.
     */
    private static Model model() {
        return localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN, true, new Sha("aaaa"), Instant.EPOCH, true, true, Option.none()),
                                Fixtures.branch(
                                        FEATURE, false, FEATURE_TIP, Instant.EPOCH, false, false, Option.none())))
                .model();
    }

    @Test
    void theLikeliestBaseIsTheOneTheBranchWasMadeFromThenTheOneItForkedFromLast() {
        List<BaseCandidate> all = List.of(MAIN_BASE, SIBLING_BASE, STAGING_BASE);

        assertEquals(List.of(STAGING_BASE, SIBLING_BASE, MAIN_BASE), BaseUpdate.rank(all, Option.none(), MAIN::equals));
        assertEquals(
                List.of(SIBLING_BASE, STAGING_BASE, MAIN_BASE), BaseUpdate.rank(all, Option.of(SIBLING), MAIN::equals));
    }

    @Test
    void aRootComesAfterABranchThatForkedAtTheSameCommit() {
        BaseCandidate development = candidate(new BranchName("development"), 32, 0);

        assertEquals(
                List.of(development, MAIN_BASE),
                BaseUpdate.rank(List.of(MAIN_BASE, development), Option.none(), MAIN::equals));
    }

    @Test
    void askingReadsWhatTheBranchCouldBeBuiltOnFromItsTip() {
        Next next = Update.update(model(), new BaseMsg.Requested(FEATURE));

        assertEquals(List.of(new ReadBaseCandidates(FEATURE, FEATURE_TIP)), next.cmds());
    }

    @Test
    void theMainBranchHasNoBaseToSet() {
        Next next = Update.update(model(), new BaseMsg.Requested(MAIN));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(new BranchError.RootHasNoBase(MAIN)), error(next));
    }

    @Test
    void theBaseOfABranchWithAnOpenPullRequestIsChangedOnGitHub() {
        PullRequest pr = new PullRequest(
                new PrNumber(12),
                new GhBranch(FEATURE.value()),
                Option.of(new GhBranch(STAGING.value())),
                false,
                Checks.NONE,
                Review.NONE,
                Option.none(),
                Option.none());
        Model withPr = model().mapGitHub(gitHub -> gitHub.withListed(List.of(pr)));

        Next next = Update.update(withPr, new BaseMsg.Requested(FEATURE));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(new BranchError.BaseFromPullRequest(FEATURE, new PrNumber(12), STAGING)), error(next));
    }

    @Test
    void theCandidatesAreOfferedRankedOnTheBaseRecordedForTheBranch() {
        Model recorded = model().mapRepo(repo -> repo.withParents(HashMap.of(FEATURE, SIBLING)));

        Model open = read(recorded, Option.of(STAGING));

        BasePopup popup = open.popup(BasePopup.class).get();
        assertEquals(List.of(STAGING_BASE, SIBLING_BASE, MAIN_BASE), popup.candidates());
        assertEquals(Option.of(SIBLING), popup.current());
        assertEquals(Option.of(STAGING), popup.createdFrom());
        assertEquals(Option.of(1), open.listCursor().line());
    }

    @Test
    void withoutABaseRecordedTheLikeliestIsHighlighted() {
        Model open = read(model(), Option.of(new BranchName("gone")));

        assertEquals(Option.none(), open.popup(BasePopup.class).get().createdFrom());
        assertEquals(Option.of(0), open.listCursor().line());
    }

    @Test
    void pickingABaseRecordsIt() {
        Next next = Update.update(read(model(), Option.none()), new BaseMsg.Chosen(Option.of(STAGING)));

        assertEquals(List.of(new SetBaseCmd(FEATURE, Option.of(STAGING))), next.cmds());
        assertEquals(Option.none(), next.model().popup(BasePopup.class));
    }

    @Test
    void pickingNoneRemovesWhatIsRecorded() {
        Model recorded = model().mapRepo(repo -> repo.withParents(HashMap.of(FEATURE, SIBLING)));

        Next next = Update.update(read(recorded, Option.none()), new BaseMsg.Chosen(Option.none()));

        assertEquals(List.of(new SetBaseCmd(FEATURE, Option.none())), next.cmds());
    }

    @Test
    void pickingTheBaseItHasJustCloses() {
        Model recorded = model().mapRepo(repo -> repo.withParents(HashMap.of(FEATURE, SIBLING)));

        Next next = Update.update(read(recorded, Option.none()), new BaseMsg.Chosen(Option.of(SIBLING)));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.none(), next.model().popup(BasePopup.class));
    }

    @Test
    void theRowsShowTheCountsAndEndInNone() {
        Model recorded = model().mapRepo(repo -> repo.withParents(HashMap.of(FEATURE, SIBLING)));
        BasePopup popup =
                read(recorded, Option.of(STAGING)).popup(BasePopup.class).get();

        List<Option<BranchName>> picked = BaseDialogs.rows(popup).map(row -> row.value());

        assertEquals(List.of(Option.of(STAGING), Option.of(SIBLING), Option.of(MAIN), Option.none()), picked);
        assertTrue(BaseDialogs.rows(popup).get(1).cells().head().text().equals("●"));
    }

    @Test
    void failingToReadTheCandidatesSaysSo() {
        Next next = Update.update(
                model(),
                new BaseMsg.CandidatesFailed(
                        FEATURE, net.vanfleteren.jazygit.state.Failure.of(new GitError.CommandFailed("boom", 1))));

        assertInstanceOf(AppError.OperationFailed.class, error(next).get());
    }

    private static Model read(Model model, Option<BranchName> createdFrom) {
        return Update.update(
                        model,
                        new BaseMsg.CandidatesRead(
                                FEATURE,
                                new BaseCandidates(List.of(MAIN_BASE, SIBLING_BASE, STAGING_BASE), createdFrom)))
                .model();
    }

    private static Option<AppError> error(Next next) {
        return next.model().popup(ErrorPopup.class).map(ErrorPopup::error);
    }
}
