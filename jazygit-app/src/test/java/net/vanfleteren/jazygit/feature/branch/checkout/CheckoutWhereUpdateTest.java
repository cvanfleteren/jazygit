package net.vanfleteren.jazygit.feature.branch.checkout;

import static net.vanfleteren.jazygit.state.TestModels.done;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.started;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchCmd;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchPopup;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.SwitchToAddedPopup;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

/**
 * Checking out in a linked worktree, which may be someone else's: the user is asked whether here, in the main
 * worktree or in a new one, and the branch of a worktree only changes where they chose it to (ADR 68).
 */
class CheckoutWhereUpdateTest {

    private static final BranchName MAIN_BRANCH = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName AGENT = new BranchName("agent");
    private static final Worktree MAIN = new Worktree(
            Path.of("/repos/app"),
            Option.of(new Head.OnBranch(MAIN_BRANCH, Option.of(new Sha("aaaa")))),
            true,
            false,
            Option.none(),
            false);
    private static final Worktree LINKED = new Worktree(
            Path.of("/repos/app-agent"),
            Option.of(new Head.OnBranch(AGENT, Option.of(new Sha("1111")))),
            false,
            true,
            Option.none(),
            false);
    private static final RemoteBranch ORIGIN_TOPIC = new RemoteBranch("origin", new BranchName("topic"));

    /**
     * {@code agent} is checked out in the linked worktree shown, {@code main} in the main worktree, {@code feature}
     * nowhere; {@code origin/topic} has no local branch.
     */
    private static Model inLinked() {
        return shown(MAIN, LINKED);
    }

    private static Model shown(Worktree... worktrees) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                new Branch(
                                        MAIN_BRANCH,
                                        false,
                                        new Sha("aaaa"),
                                        Instant.EPOCH,
                                        Option.none(),
                                        false,
                                        false,
                                        new Tracking.None(),
                                        Option.of(MAIN.path())),
                                Fixtures.branch(AGENT, true, new Sha("1111")),
                                Fixtures.branch(FEATURE, false, new Sha("ffff"))))
                .model();
        Model remotes = TestModels.remoteBranchesRead(
                        branches, List.of(new RemoteRef(ORIGIN_TOPIC, new Sha("2222"), Instant.EPOCH)))
                .model();
        return Update.update(remotes, new LoadMsg.WorktreesLoaded(List.of(worktrees)))
                .model();
    }

    private static CheckoutWherePopup asked(Model model, CheckoutRequest request) {
        return new CheckoutWherePopup(
                request, LINKED, Option.of(MAIN), model.repo().hasChanges(MAIN));
    }

    @Test
    void inTheMainWorktreeABranchIsCheckedOutWithoutAsking() {
        Model inMain = shown(new Worktree(MAIN.path(), MAIN.head(), true, true, Option.none(), false), LINKED);

        Next next = Update.update(inMain, new CheckoutMsg.Requested(FEATURE));

        assertEquals(List.of(new Checkout(FEATURE)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void inALinkedWorktreeCheckingOutABranchAsksWhere() {
        Model model = inLinked();

        Next next = Update.update(model, new CheckoutMsg.Requested(FEATURE));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(asked(model, new CheckoutRequest.Local(FEATURE))),
                next.model().popup());
    }

    @Test
    void aBranchOfAnotherWorktreeStillOffersToSwitchToIt() {
        Next next = Update.update(inLinked(), new CheckoutMsg.Requested(MAIN_BRANCH));

        assertEquals(
                Option.of(new SwitchWorktreePopup(MAIN_BRANCH, "/repos/app")),
                next.model().popup());
    }

    @Test
    void hereChecksOutInTheWorktreeShown() {
        Model asked =
                Update.update(inLinked(), new CheckoutMsg.Requested(FEATURE)).model();

        Next next = Update.update(asked, new CheckoutMsg.HereChosen());

        assertEquals(List.of(new Checkout(FEATURE)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aLockedWorktreeIsNotCheckedOutIn() {
        Worktree locked = new Worktree(LINKED.path(), LINKED.head(), false, true, Option.of("agent at work"), false);
        Model asked = Update.update(shown(MAIN, locked), new CheckoutMsg.Requested(FEATURE))
                .model();

        Next next = Update.update(asked, new CheckoutMsg.HereChosen());

        assertEquals(List.of(), next.cmds());
        assertTrue(next.model().popup().exists(CheckoutWherePopup.class::isInstance));
    }

    @Test
    void theMainWorktreeIsSwitchedToAndChecksOutOnceShown() {
        Model asked =
                Update.update(inLinked(), new CheckoutMsg.Requested(FEATURE)).model();

        Next next = Update.update(asked, new CheckoutMsg.MainChosen());

        assertEquals(
                List.of(new RecentReposCmd.SwitchTo(
                        "/repos/app",
                        new Landing(BranchesTab.LOCAL, asked.repoView().focusedPane())
                                .followedBy(new CheckoutMsg.Requested(FEATURE)))),
                next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void nextToABareRepositoryTheMainWorktreeIsNotOffered() {
        Model bare = shown(LINKED);
        Model asked = Update.update(bare, new CheckoutMsg.Requested(FEATURE)).model();

        assertEquals(
                Option.of(new CheckoutWherePopup(
                        new CheckoutRequest.Local(FEATURE), LINKED, Option.none(), Option.none())),
                asked.popup());
        assertEquals(
                List.of(), Update.update(asked, new CheckoutMsg.MainChosen()).cmds());
    }

    @Test
    void aNewWorktreeIsAskedForAndSwitchedToOnceAdded() {
        Model asked =
                Update.update(inLinked(), new CheckoutMsg.Requested(FEATURE)).model();

        Model where = Update.update(asked, new CheckoutMsg.NewWorktreeChosen()).model();
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEATURE), "/repos/app-feature", true)),
                where.popup());

        Next confirmed = Update.update(where, new Msg.PopupConfirmed(NewWorktreePopup.class));
        AddWorktreeCmd add = new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "/repos/app-feature", true);
        assertEquals(List.of(add), confirmed.cmds());

        Next added = Update.update(
                started(confirmed).model(), new AddWorktreeMsg.Added(add, "/repos/app-feature", List.empty()));
        assertTrue(added.cmds()
                .contains(new RecentReposCmd.SwitchTo(
                        "/repos/app-feature",
                        new Landing(BranchesTab.LOCAL, where.repoView().focusedPane()))));
        assertTrue(added.model()
                .popup()
                .filter(SwitchToAddedPopup.class::isInstance)
                .isEmpty());
    }

    @Test
    void aRemoteBranchIsAskedWhereToo() {
        Model model = inLinked();

        Model asked = Update.update(model, new CheckoutMsg.RemoteRequested(ORIGIN_TOPIC))
                .model();
        CheckoutRequest request = new CheckoutRequest.Remote(ORIGIN_TOPIC.branch(), ORIGIN_TOPIC);
        assertEquals(Option.of(asked(model, request)), asked.popup());

        assertEquals(
                List.of(new CheckoutRemote(ORIGIN_TOPIC.branch(), ORIGIN_TOPIC)),
                Update.update(asked, new CheckoutMsg.HereChosen()).cmds());
        assertEquals(
                new WorktreeSource.Created(ORIGIN_TOPIC.branch(), new NewBranchStart.Following(ORIGIN_TOPIC)),
                request.inNewWorktree());
        assertEquals(new CheckoutMsg.RemoteRequested(ORIGIN_TOPIC), request.again());
        // Under another name, it is that one that is checked out there.
        CheckoutRequest renamed = new CheckoutRequest.Remote(new BranchName("topic-2"), ORIGIN_TOPIC);
        assertEquals(new CheckoutMsg.Placed(renamed), renamed.again());
    }

    @Test
    void aNewBranchIsAskedWhereToo() {
        Model model = inLinked();
        Model naming = Update.update(model, new NewBranchMsg.Requested(FEATURE)).model();

        Model asked = Update.update(typed(naming, "topic"), new NewBranchMsg.Confirmed())
                .model();

        CheckoutRequest request = new CheckoutRequest.NewBranch(
                new BranchName("topic"), new BranchRef.Local(FEATURE), Option.of(FEATURE));
        assertEquals(Option.of(asked(model, request)), asked.popup());
        assertEquals(
                List.of(new NewBranchCmd(new BranchName("topic"), new BranchRef.Local(FEATURE), Option.of(FEATURE))),
                Update.update(asked, new CheckoutMsg.HereChosen()).cmds());
        assertEquals(
                new WorktreeSource.Created(
                        new BranchName("topic"),
                        new NewBranchStart.From(new BranchRef.Local(FEATURE), Option.of(FEATURE))),
                request.inNewWorktree());
    }

    @Test
    void aNewBranchInTheMainWorktreeIsCreatedWithoutAsking() {
        Model inMain = TestModels.loaded();
        Model naming =
                Update.update(inMain, new NewBranchMsg.Requested(FEATURE)).model();

        Next next = Update.update(typed(naming, "topic"), new NewBranchMsg.Confirmed());

        assertEquals(
                List.of(new NewBranchCmd(new BranchName("topic"), new BranchRef.Local(FEATURE), Option.of(FEATURE))),
                next.cmds());
        assertTrue(next.model().popup().filter(NewBranchPopup.class::isInstance).isEmpty());
    }

    @Test
    void whatALandingSaysToDoIsDoneOnceTheBranchesAndWorktreesAreRead() {
        Landing landing = new Landing(BranchesTab.LOCAL, Option.none()).followedBy(new CheckoutMsg.Requested(FEATURE));
        Model model = started(Update.init("repo", SettingsStore.Loaded.DEFAULT, Option.none(), landing))
                .model();
        model = done(model, new Cmd.LoadStatus(), new LoadMsg.StatusLoaded(TestModels.CLEAN))
                .model();
        Next branches = done(
                model,
                new Cmd.LoadBranches(),
                TestModels.branchesLoaded(new AllBranches(TestModels.BRANCHES, List.empty())));
        // The worktrees are not read yet: it waits.
        assertTrue(branches.cmds().filter(Checkout.class::isInstance).isEmpty());

        Next worktrees = done(
                started(branches).model(),
                new Cmd.LoadWorktrees(),
                new LoadMsg.WorktreesLoaded(
                        List.of(new Worktree(MAIN.path(), MAIN.head(), true, true, Option.none(), false))));

        assertTrue(worktrees.cmds().contains(new Checkout(FEATURE)));
        assertEquals(Option.none(), worktrees.model().jobs().onArrival());
    }

    @Test
    void theDialogOpensOnTheMainWorktreeOrElseOnANewOne() {
        CheckoutWherePopup withMain =
                new CheckoutWherePopup(new CheckoutRequest.Local(FEATURE), LINKED, Option.of(MAIN), Option.of(true));
        CheckoutWherePopup bare =
                new CheckoutWherePopup(new CheckoutRequest.Local(FEATURE), LINKED, Option.none(), Option.none());

        assertEquals(Option.of('m'), CheckoutDialogs.CHECKOUT_WHERE.opensAt().apply(withMain));
        assertEquals(Option.of('n'), CheckoutDialogs.CHECKOUT_WHERE.opensAt().apply(bare));
        assertEquals(
                List.of(
                        "here, in app-agent on agent",
                        "in the main worktree, app on main, which has changes",
                        "in a new worktree"),
                CheckoutDialogs.CHECKOUT_WHERE
                        .choices()
                        .map(choice -> choice.label().apply(withMain)));
    }

    @Test
    void aLockedWorktreeTellsWhyItIsNotOffered() {
        CheckoutWherePopup locked = new CheckoutWherePopup(
                new CheckoutRequest.Local(FEATURE),
                new Worktree(LINKED.path(), LINKED.head(), false, true, Option.of("agent at work"), false),
                Option.of(MAIN),
                Option.of(false));
        Choice<CheckoutWherePopup> here =
                CheckoutDialogs.CHECKOUT_WHERE.choices().head();

        assertEquals(
                "not here: app-agent is locked (agent at work)", here.label().apply(locked));
        assertEquals(false, here.enabled().test(inLinked(), locked));
        assertEquals(
                "in the main worktree, app on main",
                CheckoutDialogs.CHECKOUT_WHERE.choices().get(1).label().apply(locked));
    }
}
