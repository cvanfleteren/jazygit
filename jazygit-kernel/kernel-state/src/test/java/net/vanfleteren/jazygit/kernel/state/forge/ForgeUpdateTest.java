package net.vanfleteren.jazygit.kernel.state.forge;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.forge.ForgeDecisionTest.candidate;
import static net.vanfleteren.jazygit.kernel.state.forge.ForgeDecisionTest.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Carried;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import org.junit.jupiter.api.Test;

/**
 * The kernel switching on the forge of the home once every forge told its claims, and what the user chose before
 * was read, or asking which it is (ADR 177).
 */
class ForgeUpdateTest {

    /**
     * A repository opening with forges {@code github} and {@code forgejo}, both off until the kernel decides.
     */
    private static Model opened() {
        Model off = FeatureUpdate.off(FeatureUpdate.off(loaded(), new FeatureId("github")), new FeatureId("forgejo"));
        return ForgeUpdate.opening(
                        ForgeUpdate.opening(off, new FeatureId("github")).model(), new FeatureId("forgejo"))
                .model();
    }

    private static Model told(Model model, ForgeClaim... claims) {
        return List.of(claims)
                .foldLeft(
                        model,
                        (told, claim) -> Update.update(told, new FeatureMsg.Claimed(claim))
                                .model());
    }

    private static Model nothingRemembered(Model model) {
        return Update.update(model, new ForgeMsg.Remembered(new ForgeChoice.Unchosen()))
                .model();
    }

    @Test
    void openingReadsWhatTheUserChoseBefore() {
        assertEquals(
                List.of(new ReadForgeChoice()),
                ForgeUpdate.opening(loaded(), new FeatureId("github")).cmds());
    }

    @Test
    void theForgeOfTheHomeTurnsOnOnceEveryForgeToldAndTheChoiceWasRead() {
        Model githubTold = told(nothingRemembered(opened()), claim("github", "origin"));
        assertFalse(githubTold.on(new FeatureId("github")));

        Model decided = told(githubTold, claim("forgejo"));
        assertTrue(decided.on(new FeatureId("github")));
        assertFalse(decided.on(new FeatureId("forgejo")));
        assertEquals(Option.of(new RemoteName("origin")), ForgeHome.of(decided));
    }

    @Test
    void anotherWorktreeOfTheRepositoryOpensOnTheForgeDecidedWithoutAskingTheForgesAgain() {
        Model decided = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo"));

        Model worktree = Carried.from(decided).into(Model.initial("repo"));

        assertTrue(ForgeUpdate.known(worktree));
        assertTrue(worktree.on(new FeatureId("github")));
        assertFalse(worktree.on(new FeatureId("forgejo")));
        assertEquals(Option.of(new RemoteName("origin")), ForgeHome.of(worktree));
        // The settings panel can still ask which it is, from what the forges claimed.
        assertEquals(
                decided.part(ForgeHome.PART).candidates(),
                worktree.part(ForgeHome.PART).candidates());
    }

    @Test
    void aRepositoryOnNoForgeIsOnNoneInAnotherWorktreeEither() {
        Model decided = told(nothingRemembered(opened()), claim("github"), claim("forgejo"));

        Model worktree = Carried.from(decided).into(Model.initial("repo"));

        assertTrue(ForgeUpdate.known(worktree));
        assertFalse(worktree.on(new FeatureId("github")));
        assertFalse(worktree.on(new FeatureId("forgejo")));
    }

    @Test
    void aForgeNotDecidedYetIsDecidedAgainInAnotherWorktree() {
        Model githubTold = told(nothingRemembered(opened()), claim("github", "origin"));

        assertFalse(ForgeUpdate.known(Carried.from(githubTold).into(Model.initial("repo"))));
    }

    @Test
    void aQuestionPutOffIsAskedAgainInAnotherWorktreeAsAtAnyOpening() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));
        Model later = Update.update(asked, new ForgeMsg.Later()).model();

        assertFalse(ForgeUpdate.known(Carried.from(later).into(Model.initial("repo"))));
    }

    @Test
    void inDoubtTheUserIsAskedAndNoForgeIsOnUntilThen() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        assertEquals(
                Option.of(new ForgeChoicePopup(
                        List.of(candidate("github", "origin"), candidate("forgejo", "codeberg")),
                        Option.of(candidate("github", "origin")))),
                asked.popup(ForgeChoicePopup.class));
        assertFalse(asked.on(new FeatureId("github")));
        assertFalse(asked.on(new FeatureId("forgejo")));
    }

    @Test
    void aDialogTheUserIsInIsNotTakenAwayTheQuestionIsPutOff() {
        Model typing = nothingRemembered(opened()).openPopup(new Typing());

        Model told = told(typing, claim("github", "origin"), claim("forgejo", "codeberg"));
        assertEquals(Option.of(new Typing()), told.popup(Typing.class));
        assertFalse(told.on(new FeatureId("github")));
        assertFalse(told.on(new FeatureId("forgejo")));
        // The settings can still ask.
        assertEquals(2, told.part(ForgeHome.PART).candidates().size());
    }

    /**
     * A dialog the user is typing in.
     */
    private record Typing() implements Popup {}

    @Test
    void theChoiceTurnsItsForgeOnAndIsKept() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        Next picked = Update.update(asked, new ForgeMsg.Picked(1));
        assertTrue(picked.model().on(new FeatureId("forgejo")));
        assertFalse(picked.model().on(new FeatureId("github")));
        assertEquals(Option.none(), picked.model().popup(ForgeChoicePopup.class));
        assertEquals(Option.of(new RemoteName("codeberg")), ForgeHome.of(picked.model()));
        assertTrue(picked.cmds().contains(new SaveForgeChoice(Option.of(new RemoteName("codeberg")))));
    }

    @Test
    void choosingNoForgeIsKeptTooAndPuttingItOffIsNot() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        Next none = Update.update(asked, new ForgeMsg.Chosen(Option.none()));
        assertFalse(none.model().on(new FeatureId("github")));
        assertTrue(none.cmds().contains(new SaveForgeChoice(Option.none())));

        Next later = Update.update(asked, new ForgeMsg.Later());
        assertEquals(Option.none(), later.model().popup(ForgeChoicePopup.class));
        assertEquals(List.empty(), later.cmds());
        assertFalse(later.model().on(new FeatureId("github")));
    }

    @Test
    void choosingAgainFromTheSettingsSwitchesTheForge() {
        Model decided = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo"));

        Model asked = Update.update(decided, new ForgeMsg.Asked()).model();
        assertEquals(
                Option.of(new ForgeChoicePopup(
                        List.of(candidate("github", "origin")), Option.of(candidate("github", "origin")))),
                asked.popup(ForgeChoicePopup.class));

        Model none = Update.update(asked, new ForgeMsg.Chosen(Option.none())).model();
        assertFalse(none.on(new FeatureId("github")));
        assertEquals(Option.none(), ForgeHome.of(none));
    }

    /**
     * {@code model} with a reaction that answers each event with a {@link Seen} of it, so that the events an update
     * tells are among the commands of its {@code Next}.
     */
    private static Model watched(Model model) {
        return model.withReactions(new Reactions(List.of((told, event) -> Next.of(told, new Seen(event)))));
    }

    private static List<KernelEvent> seen(Next next) {
        return next.cmds().filter(Seen.class::isInstance).map(cmd -> ((Seen) cmd).event());
    }

    @Test
    void anotherHomeOnTheSameForgeSwitchesItOnAfresh() {
        Model decided = told(nothingRemembered(opened()), claim("forgejo", "origin", "upstream"), claim("github"));
        assertEquals(Option.of(new RemoteName("upstream")), ForgeHome.of(decided));
        Model asked = Update.update(watched(decided), new ForgeMsg.Asked()).model();

        Next origin = Update.update(asked, new ForgeMsg.Picked(0));

        assertEquals(Option.of(new RemoteName("origin")), ForgeHome.of(origin.model()));
        assertTrue(origin.model().on(new FeatureId("forgejo")));
        // Once, and only on, as it reads anew what it read of the home before (ADR 177).
        assertEquals(List.of(new KernelEvent.Switched(new FeatureId("forgejo"), true)), seen(origin));
    }

    @Test
    void theBranchesAreReadAgainAsTheHomeDecidesSinceTheBaseRemoteGoesByIt() {
        Model told = told(nothingRemembered(opened()), claim("github", "origin", "upstream"));

        Next decided = Update.update(told, new FeatureMsg.Claimed(claim("forgejo")));

        assertEquals(Option.of(new RemoteName("upstream")), ForgeHome.of(decided.model()));
        assertEquals(
                List.of(new LoadBranches(BranchBases.NONE, Option.of(new RemoteName("upstream")))),
                decided.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void theBranchesAreNotReadAgainWhenTheHomeStaysTheSame() {
        Model decided = told(nothingRemembered(opened()), claim("forgejo", "origin", "upstream"), claim("github"));
        Model asked = Update.update(decided, new ForgeMsg.Asked()).model();

        Next upstream = Update.update(asked, new ForgeMsg.Picked(1));

        assertEquals(List.empty(), upstream.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void theBranchesAreReadAgainAsTheUserPicksAnotherHome() {
        Model decided = told(nothingRemembered(opened()), claim("forgejo", "origin", "upstream"), claim("github"));
        Model asked = Update.update(decided, new ForgeMsg.Asked()).model();

        Next origin = Update.update(asked, new ForgeMsg.Picked(0));

        assertEquals(
                List.of(new LoadBranches(BranchBases.NONE, Option.of(new RemoteName("origin")))),
                origin.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void pickingTheHomeThatIsChosenAlreadySwitchesNothing() {
        Model decided = told(nothingRemembered(opened()), claim("forgejo", "origin", "upstream"), claim("github"));
        Model asked = Update.update(watched(decided), new ForgeMsg.Asked()).model();

        Next upstream = Update.update(asked, new ForgeMsg.Picked(1));

        assertEquals(Option.of(new RemoteName("upstream")), ForgeHome.of(upstream.model()));
        assertTrue(upstream.model().on(new FeatureId("forgejo")));
        assertEquals(List.empty(), seen(upstream));
    }

    /**
     * What the reaction of {@link #watched} answers {@code event} with; it is never run.
     */
    private record Seen(KernelEvent event) implements Cmd {

        @Override
        public Msg run(Env env) {
            throw new UnsupportedOperationException("never run");
        }

        @Override
        public Msg failed(Failure failure) {
            throw new UnsupportedOperationException("never run");
        }
    }

    @Test
    void aRememberedChoiceIsHonoured() {
        Model remembered = Update.update(
                        opened(), new ForgeMsg.Remembered(new ForgeChoice.Remote(new RemoteName("codeberg"))))
                .model();

        Model decided = told(remembered, claim("github", "origin"), claim("forgejo", "codeberg"));
        assertTrue(decided.on(new FeatureId("forgejo")));
        assertEquals(Option.none(), decided.popup(ForgeChoicePopup.class));
    }

    @Test
    void itDecidesOnceAnOpeningAndIgnoresAForgeItDoesNotWaitFor() {
        Model decided = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo"));

        Model again = told(decided, claim("forgejo", "codeberg"), claim("other", "x"));
        assertEquals(decided, again);
    }
}
