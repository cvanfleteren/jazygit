package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStashes;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The branch transitions: checkout, creating and deleting branches.
 */
class BranchUpdateTest {

    @Test
    void checkoutRequestOfAnotherBranchChecksItOut() {
        Model failedBefore = Update.update(loaded(), failed(new BranchCmd.Checkout(new Branch.Name("feature")), "boom"))
                .model();

        Next next = Update.update(failedBefore, new CheckoutMsg.Requested(new Branch.Name("feature")));

        assertEquals(List.of(new Checkout(new Branch.Name("feature"))), next.cmds());
        assertEquals(Option.none(), next.model().error());
    }

    @Test
    void checkoutRefusedForLocalChangesAsksToStashThem() {
        Next next = Update.update(
                loaded(),
                new CheckoutMsg.NeedsStash(
                        new BranchCmd.Checkout(new Branch.Name("feature")),
                        net.vanfleteren.jazygit.git.StubGitProvider.executed("git checkout --quiet feature --")));

        assertEquals(
                Option.of(new StashCheckoutPopup(new Branch.Name("feature"))),
                next.model().popup(StashCheckoutPopup.class));
        // The checkout that ran is logged like any other.
        assertEquals(
                List.of("git checkout --quiet feature --"),
                next.model().commandLog().flatMap(entry -> entry.commands()).map(command -> command.line()));
    }

    @Test
    void theOfferToStashDoesNotTakeAwayADialogTheUserIsTypingIn() {
        Model typing = Update.update(tracking(), new NewBranchMsg.Requested(new Branch.Name("main")))
                .model();

        Model next = Update.update(
                        typing,
                        new CheckoutMsg.NeedsStash(new BranchCmd.Checkout(new Branch.Name("feature")), List.empty()))
                .model();

        assertEquals(typing.popup(), next.popup());
    }

    @Test
    void aBusyBranchIsNotCheckedOutMergedRebasedOrBranchedFrom() {
        Model pushing = tracking()
                .withBusyBranch(
                        new BranchRef.Local(new Branch.Name("feature")), net.vanfleteren.jazygit.state.Operation.PUSH);

        assertEquals(
                List.of(),
                Update.update(pushing, new CheckoutMsg.Requested(new Branch.Name("feature")))
                        .cmds());
        assertEquals(
                Option.none(),
                Update.update(pushing, new MergeMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(pushing, new RebaseMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(pushing, new NewBranchMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());
    }

    @Test
    void checkoutRequestWithChangedFilesStillTriesTheCheckoutFirst() {
        Model dirty = Update.update(
                        loaded(),
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();

        assertEquals(
                List.of(new Checkout(new Branch.Name("feature"))),
                Update.update(dirty, new CheckoutMsg.Requested(new Branch.Name("feature")))
                        .cmds());
    }

    @Test
    void confirmingTheStashChecksOutThroughAStashAndClosesThePopup() {
        Model asked = Update.update(
                        loaded(),
                        new CheckoutMsg.NeedsStash(new BranchCmd.Checkout(new Branch.Name("feature")), List.empty()))
                .model();

        Next next = Update.update(asked, new CheckoutMsg.StashConfirmed());

        assertEquals(List.of(new CheckoutWithStash(new Branch.Name("feature"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void cancellingTheStashClosesThePopupWithoutCheckingOut() {
        Model asked = Update.update(
                        loaded(),
                        new CheckoutMsg.NeedsStash(new BranchCmd.Checkout(new Branch.Name("feature")), List.empty()))
                .model();

        Next next = Update.update(asked, new CheckoutMsg.StashCancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void confirmingWithoutAnOpenPopupDoesNothing() {
        assertEquals(
                List.of(),
                Update.update(loaded(), new CheckoutMsg.StashConfirmed()).cmds());
    }

    @Test
    void checkoutRequestIsIgnoredForTheCurrentOrAnUnknownBranch() {
        Model model = loaded();

        assertEquals(
                List.of(),
                Update.update(model, new CheckoutMsg.Requested(new Branch.Name("main")))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(model, new CheckoutMsg.Requested(new Branch.Name("gone")))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(Update.init("repo").model(), new CheckoutMsg.Requested(new Branch.Name("feature")))
                        .cmds());
    }

    /**
     * The loaded model, with feature tracking origin/feature.
     */
    private static Model tracking() {
        return Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("feature"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(
                                                new RemoteBranch("origin", new Branch.Name("feature")), 0, 0))))))
                .model();
    }

    @Test
    void pushRequestOfABranchWithoutUpstreamAsksWhereToPushIt() {
        Next asked = Update.update(
                withCheckoutError(loaded(), "foo", "boom"), new PushMsg.Requested(new Branch.Name("feature")));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new SetUpstreamPopup(new Branch.Name("feature"))),
                asked.model().popup());
        assertEquals(Option.none(), asked.model().error());
        assertEquals(io.vavr.collection.HashSet.empty(), asked.model().busy().keySet());

        Next confirmed = Update.update(asked.model(), new PushMsg.UpstreamConfirmed("  upstream   shared/feature "));
        assertEquals(
                List.of(new BranchCmd.PushSettingUpstream(
                        new Branch.Name("feature"), new RemoteBranch("upstream", new Branch.Name("shared/feature")))),
                confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                io.vavr.collection.HashSet.of(new BranchRef.Local(new Branch.Name("feature"))),
                confirmed.model().busy().keySet());

        Next cancelled = Update.update(asked.model(), new PushMsg.UpstreamCancelled());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void aBranchThatFollowsALocalBranchIsPushedWhereTheUserSaysRatherThanOntoThatBranch() {
        Model model = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("feature"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(
                                                new Upstream(new RemoteBranch(".", new Branch.Name("main")), 1, 0))))))
                .model();

        Next asked = Update.update(model, new PushMsg.Requested(new Branch.Name("feature")));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new SetUpstreamPopup(new Branch.Name("feature"))),
                asked.model().popup());
        assertEquals(
                new RemoteBranch("origin", new Branch.Name("feature")),
                model.branch(new Branch.Name("feature")).get().pushTarget());
    }

    @Test
    void anUpstreamThatIsNotARemoteAndABranchKeepsTheDialogOpen() {
        Model asked = Update.update(loaded(), new PushMsg.Requested(new Branch.Name("feature")))
                .model();

        for (String text : List.of("", "origin", "origin feature extra")) {
            Next next = Update.update(asked, new PushMsg.UpstreamConfirmed(text));
            assertEquals(List.of(), next.cmds());
            assertEquals(
                    Option.of(new SetUpstreamPopup(new Branch.Name("feature"))),
                    next.model().popup());
            assertEquals(true, next.model().error().isDefined());
        }
    }

    @Test
    void confirmingAnUpstreamWithoutTheDialogDoesNothing() {
        assertEquals(
                List.of(),
                Update.update(loaded(), new PushMsg.UpstreamConfirmed("origin feature"))
                        .cmds());
    }

    @Test
    void pushRequestOfAKnownBranchPushesIt() {
        Model failedBefore = Update.update(
                        tracking(),
                        failed(
                                new BranchCmd.Push(
                                        new Branch.Name("feature"),
                                        new RemoteBranch("origin", new Branch.Name("feature")),
                                        PushMode.FAST_FORWARD),
                                "boom"))
                .model();

        Next next = Update.update(failedBefore, new PushMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                List.of(new BranchCmd.Push(
                        new Branch.Name("feature"),
                        new RemoteBranch("origin", new Branch.Name("feature")),
                        PushMode.FAST_FORWARD)),
                next.cmds());
        assertEquals(Option.none(), next.model().error());
        assertEquals(
                io.vavr.collection.HashSet.of(new BranchRef.Local(new Branch.Name("feature"))),
                next.model().busy().keySet());
        // Not pushed again while it is being pushed.
        assertEquals(
                List.of(),
                Update.update(next.model(), new PushMsg.Requested(new Branch.Name("feature")))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(loaded(), new PushMsg.Requested(new Branch.Name("gone")))
                        .cmds());
    }

    @Test
    void pushRequestOfADivergedBranchAsksForConfirmationInsteadOfPushing() {
        Model diverged = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("feature"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(
                                                new RemoteBranch("origin", new Branch.Name("feature")), 1, 2))))))
                .model();

        Next asked = Update.update(diverged, new PushMsg.Requested(new Branch.Name("feature")));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new ForcePushPopup(new Branch.Name("feature"))),
                asked.model().popup());
        assertEquals(io.vavr.collection.HashSet.empty(), asked.model().busy().keySet());

        Next confirmed = Update.update(asked.model(), new PushMsg.ForceConfirmed());
        assertEquals(
                List.of(new BranchCmd.Push(
                        new Branch.Name("feature"),
                        new RemoteBranch("origin", new Branch.Name("feature")),
                        PushMode.FORCE_WITH_LEASE)),
                confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                io.vavr.collection.HashSet.of(new BranchRef.Local(new Branch.Name("feature"))),
                confirmed.model().busy().keySet());

        Next cancelled = Update.update(asked.model(), new PushMsg.ForceCancelled());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void confirmingWithoutAPromptDoesNothing() {
        assertEquals(
                List.of(), Update.update(loaded(), new PushMsg.ForceConfirmed()).cmds());
    }

    @Test
    void failedPushIsStoredAndASuccessfulOneReloadsTheBranches() {
        Model failed = Update.update(
                        loaded(),
                        failed(
                                new BranchCmd.Push(
                                        new Branch.Name("feature"),
                                        new RemoteBranch("origin", new Branch.Name("feature")),
                                        PushMode.FAST_FORWARD),
                                "boom"))
                .model();
        assertEquals(Option.of("Pushing branch feature failed: boom"), errorText(failed));

        Model pushing = Update.update(loaded(), new PushMsg.Requested(new Branch.Name("feature")))
                .model();
        assertEquals(
                io.vavr.collection.HashSet.empty(),
                Update.update(
                                pushing,
                                failed(
                                        new BranchCmd.Push(
                                                new Branch.Name("feature"),
                                                new RemoteBranch("origin", new Branch.Name("feature")),
                                                PushMode.FAST_FORWARD),
                                        "boom"))
                        .model()
                        .busy()
                        .keySet());
        assertEquals(
                io.vavr.collection.HashSet.empty(),
                Update.update(
                                pushing,
                                succeeded(new BranchCmd.Push(
                                        new Branch.Name("feature"),
                                        new RemoteBranch("origin", new Branch.Name("feature")),
                                        PushMode.FAST_FORWARD)))
                        .model()
                        .busy()
                        .keySet());

        Next done = Update.update(
                withCheckoutError(loaded(), "foo", "boom"),
                succeeded(new BranchCmd.Push(
                        new Branch.Name("feature"),
                        new RemoteBranch("origin", new Branch.Name("feature")),
                        PushMode.FAST_FORWARD)));
        assertEquals(Option.none(), done.model().error());
        assertEquals(true, done.cmds().contains(new LoadBranches()));
    }

    /**
     * The loaded model, with main checked out and feature not, both tracking their remote branch.
     */
    private static Model pullable(int mainAhead, int featureAhead) {
        return Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(
                                        new Branch.Name("main"),
                                        true,
                                        new Sha("aaaa"),
                                        Instant.EPOCH,
                                        true,
                                        true,
                                        Option.of(new Upstream(
                                                new RemoteBranch("origin", new Branch.Name("main")), mainAhead, 1))),
                                new Branch(
                                        new Branch.Name("feature"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(
                                                new RemoteBranch("origin", new Branch.Name("feature")),
                                                featureAhead,
                                                1))))))
                .model();
    }

    @Test
    void pullRequestOfTheCheckedOutBranchPullsItEvenWhenItHasDiverged() {
        Model failedBefore = Update.update(pullable(2, 0), failed(new BranchCmd.Pull(new Branch.Name("main")), "boom"))
                .model();

        Next next = Update.update(failedBefore, new PullMsg.Requested(new Branch.Name("main")));

        assertEquals(List.of(new BranchCmd.Pull(new Branch.Name("main"))), next.cmds());
        assertEquals(Option.none(), next.model().error());
        assertEquals(
                io.vavr.collection.HashMap.of(
                        new BranchRef.Local(new Branch.Name("main")), net.vanfleteren.jazygit.state.Operation.PULL),
                next.model().busy());
        // Not pulled again while it is being pulled.
        assertEquals(
                List.of(),
                Update.update(next.model(), new PullMsg.Requested(new Branch.Name("main")))
                        .cmds());
    }

    @Test
    void pullRequestOfAnotherBranchFastForwardsIt() {
        Next next = Update.update(pullable(0, 0), new PullMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                List.of(new BranchCmd.FastForward(
                        new Branch.Name("feature"), new RemoteBranch("origin", new Branch.Name("feature")))),
                next.cmds());
        assertEquals(
                io.vavr.collection.HashSet.of(new BranchRef.Local(new Branch.Name("feature"))),
                next.model().busy().keySet());
    }

    @Test
    void anotherBranchWithCommitsToPushIsNotFastForwarded() {
        Next next = Update.update(pullable(0, 1), new PullMsg.Requested(new Branch.Name("feature")));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of("Cannot fast-forward branch feature: it has commits that are not pushed"),
                errorText(next.model()));
    }

    @Test
    void aBranchWithoutUpstreamIsNotPulled() {
        Next current = Update.update(loaded(), new PullMsg.Requested(new Branch.Name("main")));
        assertEquals(List.of(), current.cmds());
        assertEquals(Option.of("Cannot pull branch main: it has no upstream"), errorText(current.model()));

        assertEquals(
                List.of(),
                Update.update(loaded(), new PullMsg.Requested(new Branch.Name("feature")))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(loaded(), new PullMsg.Requested(new Branch.Name("gone")))
                        .cmds());
    }

    @Test
    void aBranchWhoseUpstreamIsGoneIsNotPulledAndTheErrorSaysWhy() {
        Branch gone = new Branch(
                new Branch.Name("old"),
                false,
                new Sha("9"),
                Instant.EPOCH,
                false,
                false,
                new Tracking.Gone(new RemoteBranch("origin", new Branch.Name("old"))));
        Model model = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(gone)))
                .model();

        Next pulled = Update.update(model, new PullMsg.Requested(new Branch.Name("old")));

        assertEquals(List.of(), pulled.cmds());
        assertEquals(
                Option.of("Cannot pull branch old: its upstream origin/old was deleted on the remote"),
                errorText(pulled.model()));
        // Like a branch without an upstream, it asks where to push.
        assertEquals(
                Option.of(new SetUpstreamPopup(new Branch.Name("old"))),
                Update.update(model, new PushMsg.Requested(new Branch.Name("old")))
                        .model()
                        .popup());
    }

    @Test
    void failedPullIsStored() {
        assertEquals(
                Option.of("Pulling branch main failed: boom"),
                errorText(Update.update(loaded(), failed(new BranchCmd.Pull(new Branch.Name("main")), "boom"))
                        .model()));
        assertEquals(
                Option.of("Fast-forwarding branch feature failed: boom"),
                errorText(Update.update(
                                loaded(),
                                failed(
                                        new BranchCmd.FastForward(
                                                new Branch.Name("feature"),
                                                new RemoteBranch("origin", new Branch.Name("feature"))),
                                        "boom"))
                        .model()));
    }

    @Test
    void newBranchRequestOpensTheDialogAndCancelClosesIt() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested(new Branch.Name("feature")))
                .model();
        assertEquals(Option.of(new NewBranchPopup("feature")), open.popup());

        Next cancelled = Update.update(open, new NewBranchMsg.Cancelled());
        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
    }

    @Test
    void newBranchRequestedAtACommitOpensTheDialogWithItsSha() {
        net.vanfleteren.jazygit.git.model.Commit commit = new net.vanfleteren.jazygit.git.model.Commit(
                new Sha("abc1234567"), "Ada", "a@e.com", java.time.Instant.EPOCH, "msg", "");
        Model model = Update.update(loaded(), new net.vanfleteren.jazygit.state.LoadMsg.CommitsLoaded(List.of(commit)))
                .model();

        assertEquals(
                Option.of(new NewBranchPopup("abc1234567", "abc1234")),
                Update.update(model, new NewBranchMsg.RequestedAtCommit(commit.sha()))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(model, new NewBranchMsg.RequestedAtCommit(new Sha("elsewhere")))
                        .model()
                        .popup());
    }

    @Test
    void confirmedNewBranchIsCreatedAtTheBase() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed(" topic "));

        assertEquals(List.of(new BranchCmd.CreateBranch(new Branch.Name("topic"), "feature")), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void blankNewBranchNameKeepsTheDialogOpen() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed("  "));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of(new NewBranchPopup("feature")), next.model().popup());
    }

    @Test
    void failedBranchCreationIsStored() {
        Model model = Update.update(
                        loaded(), failed(new BranchCmd.CreateBranch(new Branch.Name("topic"), "main"), "boom"))
                .model();

        assertEquals(Option.of("Creating branch topic failed: boom"), errorText(model));
    }

    @Test
    void renameRequestOpensTheDialogAndCancelClosesIt() {
        Model open = Update.update(loaded(), new RenameBranchMsg.Requested(new Branch.Name("feature")))
                .model();
        assertEquals(Option.of(new RenameBranchPopup(new Branch.Name("feature"))), open.popup());

        Next cancelled = Update.update(open, new RenameBranchMsg.Cancelled());
        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
    }

    @Test
    void theCheckedOutBranchCanBeRenamedButNotABusyOrUnknownOne() {
        Model pushing = loaded().withBusyBranch(
                        new BranchRef.Local(new Branch.Name("feature")), net.vanfleteren.jazygit.state.Operation.PUSH);

        assertEquals(
                Option.of(new RenameBranchPopup(new Branch.Name("main"))),
                Update.update(pushing, new RenameBranchMsg.Requested(new Branch.Name("main")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(pushing, new RenameBranchMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(pushing, new RenameBranchMsg.Requested(new Branch.Name("nope")))
                        .model()
                        .popup());
    }

    @Test
    void confirmedRenameRenamesTheBranch() {
        Model open = Update.update(loaded(), new RenameBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new RenameBranchMsg.Confirmed(" topic "));

        assertEquals(
                List.of(new BranchCmd.RenameBranch(new Branch.Name("feature"), new Branch.Name("topic"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void blankRenameKeepsTheDialogOpenAndTheSameNameClosesIt() {
        Model open = Update.update(loaded(), new RenameBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next blank = Update.update(open, new RenameBranchMsg.Confirmed("  "));
        assertEquals(List.of(), blank.cmds());
        assertEquals(
                Option.of(new RenameBranchPopup(new Branch.Name("feature"))),
                blank.model().popup());

        Next same = Update.update(open, new RenameBranchMsg.Confirmed("feature "));
        assertEquals(List.of(), same.cmds());
        assertEquals(Option.none(), same.model().popup());
    }

    @Test
    void failedRenameIsStored() {
        Model model = Update.update(
                        loaded(),
                        failed(new BranchCmd.RenameBranch(new Branch.Name("feature"), new Branch.Name("main")), "boom"))
                .model();

        assertEquals(Option.of("Renaming branch feature failed: boom"), errorText(model));
    }

    @Test
    void deleteRequestOpensThePopupOnlyForAnotherKnownBranch() {
        Model model = loaded();

        assertEquals(
                Option.of(new DeleteBranchPopup(new Branch.Name("feature"))),
                Update.update(model, new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(model, new DeleteBranchMsg.Requested(new Branch.Name("main")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(model, new DeleteBranchMsg.Requested(new Branch.Name("gone")))
                        .model()
                        .popup());
    }

    @Test
    void remoteScopesAreRefusedForALocalOnlyBranch() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        for (DeleteScope scope : List.of(DeleteScope.REMOTE, DeleteScope.BOTH)) {
            Next next = Update.update(open, new DeleteBranchMsg.Chosen(scope));
            assertEquals(List.of(), next.cmds());
            assertEquals(
                    Option.of(new DeleteBranchPopup(new Branch.Name("feature"))),
                    next.model().popup());
        }
        assertEquals(
                List.of(new BranchCmd.DeleteBranch(
                        new Branch.Name("feature"),
                        new RemoteBranch("origin", new Branch.Name("feature")),
                        DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.LOCAL))
                        .cmds());
    }

    @Test
    void remoteScopesAreRefusedForTheDefaultBranch() {
        Model model = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("trunk"),
                                        false,
                                        new Sha("tttt"),
                                        java.time.Instant.EPOCH,
                                        true,
                                        true))))
                .model();
        Model open = Update.update(model, new DeleteBranchMsg.Requested(new Branch.Name("trunk")))
                .model();

        assertEquals(
                List.of(),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.REMOTE))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.BOTH))
                        .cmds());
        assertEquals(
                List.of(new BranchCmd.DeleteBranch(
                        new Branch.Name("trunk"),
                        new RemoteBranch("origin", new Branch.Name("trunk")),
                        DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.LOCAL))
                        .cmds());
    }

    @Test
    void choosingAScopeDeletesTheBranchAndClosesThePopup() {
        Model withRemote = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("feature"),
                                        false,
                                        new Sha("ffff"),
                                        java.time.Instant.EPOCH,
                                        true))))
                .model();
        Model open = Update.update(withRemote, new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.REMOTE));

        assertEquals(
                List.of(new BranchCmd.DeleteBranch(
                        new Branch.Name("feature"),
                        new RemoteBranch("origin", new Branch.Name("feature")),
                        DeleteScope.REMOTE)),
                next.cmds());
        assertEquals(Option.none(), next.model().popup());
        assertEquals(
                io.vavr.collection.HashSet.of(new BranchRef.Local(new Branch.Name("feature"))),
                next.model().busy().keySet());
    }

    @Test
    void aBranchBeingDeletedIsBusyUntilTheDeleteIsDone() {
        Model deleting = Update.update(
                        Update.update(tracking(), new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                                .model(),
                        new DeleteBranchMsg.Chosen(DeleteScope.BOTH))
                .model();

        // Neither pushed nor deleted again meanwhile.
        assertEquals(
                List.of(),
                Update.update(deleting, new PushMsg.Requested(new Branch.Name("feature")))
                        .cmds());
        assertEquals(
                Option.none(),
                Update.update(deleting, new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                        .model()
                        .popup());

        assertEquals(
                io.vavr.collection.HashSet.empty(),
                Update.update(
                                deleting,
                                succeeded(new BranchCmd.DeleteBranch(
                                        new Branch.Name("feature"),
                                        new RemoteBranch("origin", new Branch.Name("feature")),
                                        DeleteScope.LOCAL)))
                        .model()
                        .busy()
                        .keySet());
        assertEquals(
                io.vavr.collection.HashSet.empty(),
                Update.update(
                                deleting,
                                failed(
                                        new BranchCmd.DeleteBranch(
                                                new Branch.Name("feature"),
                                                new RemoteBranch("origin", new Branch.Name("feature")),
                                                DeleteScope.LOCAL),
                                        "boom"))
                        .model()
                        .busy()
                        .keySet());
    }

    @Test
    void cancellingClosesThePopupWithoutDeleting() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new DeleteBranchMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void failedDeletionIsStored() {
        Model model = Update.update(
                        loaded(),
                        failed(
                                new BranchCmd.DeleteBranch(
                                        new Branch.Name("feature"),
                                        new RemoteBranch("origin", new Branch.Name("feature")),
                                        DeleteScope.LOCAL),
                                "boom"))
                .model();

        assertEquals(Option.of("Deleting branch feature failed: boom"), errorText(model));
    }

    @Test
    void checkedOutReloadsStatusAndBranches() {
        Next next = Update.update(
                loaded(), succeeded(new BranchCmd.Checkout(new Branch.Name("feature")), "git checkout feature"));

        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadStashes()),
                next.cmds());
        assertEquals(Option.none(), next.model().error());
    }

    @Test
    void failedCheckoutIsStoredAndTheRepositoryReadAgain() {
        Next next = Update.update(
                loaded(),
                failed(new BranchCmd.Checkout(new Branch.Name("feature")), "local changes would be overwritten"));

        assertEquals(
                Option.of("Checkout of feature failed: local changes would be overwritten"), errorText(next.model()));
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(),
                        new LoadStashes()),
                next.cmds());
    }

    @Test
    void rebaseRequestOfAnotherBranchOpensTheChoiceForTheCheckedOutOne() {
        Model model = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("1")),
                                new Branch(new Branch.Name("feature"), false, new Sha("2")))))
                .model();

        Next next = Update.update(model, new RebaseMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(new RebasePopup(new Branch.Name("main"), new Branch.Name("feature"))),
                next.model().popup(RebasePopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theCheckedOutBranchAndUnknownBranchesCannotBeRebasedOnto() {
        Model model = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("1")),
                                new Branch(new Branch.Name("feature"), false, new Sha("2")))))
                .model();

        assertEquals(
                Option.none(),
                Update.update(model, new RebaseMsg.Requested(new Branch.Name("main")))
                        .model()
                        .popup(RebasePopup.class));
        assertEquals(
                Option.none(),
                Update.update(model, new RebaseMsg.Requested(new Branch.Name("nope")))
                        .model()
                        .popup(RebasePopup.class));
    }

    @Test
    void choosingARebaseClosesThePopupAndRebases() {
        Model open = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("1")),
                                new Branch(new Branch.Name("feature"), false, new Sha("2")))))
                .model();
        open = Update.update(open, new RebaseMsg.Requested(new Branch.Name("feature")))
                .model();

        Next next = Update.update(open, new RebaseMsg.Chosen());

        assertEquals(List.of(new BranchCmd.Rebase(new Branch.Name("feature"))), next.cmds());
        assertEquals(Option.none(), next.model().popup(RebasePopup.class));
    }

    @Test
    void aFailedRebaseShowsTheError() {
        Next next = Update.update(loaded(), failed(new BranchCmd.Rebase(new Branch.Name("feature")), "conflict"));

        assertEquals(Option.of("Rebasing onto feature failed: conflict"), errorText(next.model()));
    }
}
