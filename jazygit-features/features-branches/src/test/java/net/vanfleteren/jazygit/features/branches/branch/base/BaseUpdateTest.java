package net.vanfleteren.jazygit.features.branches.branch.base;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.localBranchesRead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.BaseCandidate;
import net.vanfleteren.jazygit.core.git.model.BaseCandidates;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.StatedBases;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Setting the branch a branch is built on (ADR 143).
 */
class BaseUpdateTest {
    // A feature that states what the branches are built on, as GitHub's does from its pull requests (ADR 169).
    private static final Part<StatedBases> STATING =
            Part.of("test.bases", StatedBases.NONE).stating(bases -> bases);

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

        assertEquals(
                List.of(new ReadBaseCandidates(FEATURE, FEATURE_TIP, Option.of(RemoteBranch.DEFAULT_REMOTE))),
                next.cmds());
    }

    @Test
    void theMainBranchHasNoBaseToSet() {
        Next next = Update.update(model(), new BaseMsg.Requested(MAIN));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(new BranchError.RootHasNoBase(MAIN)), error(next));
    }

    @Test
    void theBaseAFeatureStatesIsChangedWhereItIsStated() {
        Phrase by = Phrase.verbatim("its pull request #12");
        Model stated = model().withPart(
                        STATING,
                        new StatedBases(
                                HashMap.of(FEATURE, new StatedBases.Stated(STAGING, by)),
                                HashSet.empty(),
                                Option.none()));

        Next next = Update.update(stated, new BaseMsg.Requested(FEATURE));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(new BranchError.BaseStated(FEATURE, STAGING, by)), error(next));
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
    void aBranchStackedOnTheBranchIsNotOfferedOnceTheBranchMovedOn() {
        // sibling is built on feature, and next on sibling; feature got a commit since, so git offers both.
        BranchName next = new BranchName("next");
        Model stacked = model().mapRepo(repo -> repo.withParents(HashMap.of(SIBLING, FEATURE, next, SIBLING)));

        Model open = Update.update(
                        stacked,
                        new BaseMsg.CandidatesRead(
                                FEATURE,
                                new BaseCandidates(
                                        List.of(MAIN_BASE, candidate(SIBLING, 1, 2), candidate(next, 1, 3)),
                                        Option.of(SIBLING))))
                .model();

        BasePopup popup = open.popup(BasePopup.class).get();
        assertEquals(List.of(MAIN_BASE), popup.candidates());
        assertEquals(Option.none(), popup.createdFrom());
    }

    @Test
    void aCycleAmongTheParentsEndsTheWalk() {
        BranchName other = new BranchName("other");

        assertEquals(false, BaseUpdate.builtOn(HashMap.of(SIBLING, other, other, SIBLING), SIBLING, FEATURE));
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
    void pickingNoneRemovesARecordedBaseThatIsGone() {
        // feature records a base that is gone, so the model has none for it, as when nothing is recorded.
        Next next = Update.update(read(model(), Option.none()), new BaseMsg.Chosen(Option.none()));

        assertEquals(List.of(new SetBaseCmd(FEATURE, Option.none())), next.cmds());
    }

    @Test
    void whatIsReadForABranchThatIsGoneMeanwhileOffersNothing() {
        Model gone = localBranchesRead(
                        model(),
                        List.of(Fixtures.branch(MAIN, true, new Sha("aaaa"), Instant.EPOCH, true, true, Option.none())))
                .model();

        Model next = read(gone, Option.none());

        assertEquals(Option.none(), next.popup(BasePopup.class));
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
                        FEATURE,
                        net.vanfleteren.jazygit.kernel.state.Failure.of(new GitError.CommandFailed("boom", 1))));

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
