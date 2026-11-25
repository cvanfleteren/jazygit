package net.vanfleteren.jazygit.features.branches.branch;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * A branch that a worktree rebases, with its HEAD detached meanwhile, and that stopped (ADR 197): in the worktree
 * shown, it is the one worked on, and checking it out says to finish the rebase; in another one, switching there says
 * that it stopped.
 */
class RebasingBranchUpdateTest {

    private static final Path LINKED = Path.of("/repos/linked");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName MAIN = new BranchName("main");
    private static final Sha MAIN_TIP = new Sha("aaaa");
    private static final InProgress.Rebasing REBASING = new InProgress.Rebasing(Option.of(FEATURE), MAIN_TIP, 1, 2);
    private static final FileEntry CONFLICTED =
            FileEntry.conflicted("f.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

    private static Branch feature(Option<Path> worktree) {
        return new Branch(
                FEATURE,
                false,
                new Sha("ffff"),
                Instant.EPOCH,
                Option.none(),
                false,
                false,
                new Tracking.None(),
                worktree);
    }

    /**
     * The worktree shown rebases {@code feature}, stopped on a conflict, with HEAD detached at the tip of main.
     */
    private static Model rebasingHere() {
        Model model = TestModels.localBranchesRead(
                        loaded(), List.of(feature(Option.none()), Fixtures.branch(MAIN, false, MAIN_TIP)))
                .model();
        return model.mapRepo(repo -> repo.withStatus(new Loadable.Loaded<>(
                new RepoStatus(new Head.Detached(MAIN_TIP), List.of(CONFLICTED), Option.of(REBASING)))));
    }

    /**
     * The worktree shown has main checked out; the linked one rebases {@code feature}, stopped on a conflict.
     */
    private static Model rebasingElsewhere() {
        Model model = TestModels.localBranchesRead(
                        loaded(), List.of(Fixtures.branch(MAIN, true, MAIN_TIP), feature(Option.of(LINKED))))
                .model();
        Worktree linked = new Worktree(
                        LINKED, Option.of(new Head.Detached(MAIN_TIP)), false, false, Option.none(), false)
                .withBusyWith(Option.of(FEATURE))
                .withMidway(Option.of(new Midway(REBASING, 1)));
        return model.mapRepo(repo -> repo.withWorktrees(new Loadable.Loaded<>(List.of(linked))));
    }

    @Test
    void theBranchRebasedHereIsTheOneWorkedOnWithWhatTheRebaseIsAt() {
        Model model = rebasingHere();

        assertEquals(Option.of(FEATURE), Branches.rebasedHere(model));
        assertEquals(
                Option.of(new Midway(REBASING, 1)),
                Branches.midway(model, model.repo().branch(FEATURE).get()));
        assertEquals(
                Option.none(), Branches.midway(model, model.repo().branch(MAIN).get()));
    }

    @Test
    void checkingOutTheBranchRebasedHereSaysToFinishTheRebaseFirst() {
        Next asked = Update.update(rebasingHere(), new CheckoutMsg.Requested(FEATURE));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of("feature is being rebased here; continue or abort the rebase first (M)"),
                errorText(asked.model()));
    }

    @Test
    void aBranchAnotherWorktreeRebasesSaysWhereItStoppedAndSwitchingThereSaysItToo() {
        Model model = rebasingElsewhere();

        assertEquals(Option.none(), Branches.rebasedHere(model));
        assertEquals(
                Option.of(new Midway(REBASING, 1)),
                Branches.midway(model, model.repo().branch(FEATURE).get()));

        Next asked = Update.update(model, new CheckoutMsg.Requested(FEATURE));

        assertEquals(
                Option.of(new SwitchWorktreePopup(FEATURE, LINKED.toString(), Option.of(new Midway(REBASING, 1)))),
                asked.model().popup());
    }
}
