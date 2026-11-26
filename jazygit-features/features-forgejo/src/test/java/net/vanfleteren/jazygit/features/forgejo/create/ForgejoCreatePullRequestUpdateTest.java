package net.vanfleteren.jazygit.features.forgejo.create;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.TIME;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.following;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg;
import net.vanfleteren.jazygit.features.forgejo.create.ForgejoCreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures;
import net.vanfleteren.jazygit.features.forgejo.pulls.ReadPullRequests;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;
import org.junit.jupiter.api.Test;

/**
 * Creating the pull request of a branch on Forgejo: where it is pushed to, the bases, the dialog, and what creating it
 * does (ADR 178). What it does alike on every forge is {@link ForgejoCreatePullRequestContractTest}'s.
 */
class ForgejoCreatePullRequestUpdateTest {

    private static final BranchName BRANCH = new BranchName("feature");
    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName NEXT = new BranchName("next");
    private static final RemoteName UPSTREAM = new RemoteName("upstream");
    private static final ForgejoPullRequest CREATED = new ForgejoPullRequest(
            13, "Create it", "feature", Option.of("cvf/nix"), "main", false, "https://code.example.net/cvf/nix/13");

    private static RemoteRef remote(RemoteName remote, String name) {
        return new RemoteRef(new RemoteBranch(remote, new BranchName(name)), new Sha("e".repeat(40)), TIME);
    }

    private static Branch main() {
        Branch main = following("main", Option.of("main"));
        return new Branch(
                main.name(),
                main.current(),
                main.tip(),
                main.tipTime(),
                main.created(),
                main.hasRemote(),
                true,
                main.tracking(),
                main.worktree());
    }

    /**
     * A model with {@code local} branches and the branches of origin, the home, read: main, next, feature and HEAD.
     */
    private static Model model(List<Branch> local) {
        Model model = TestModels.localBranchesRead(TestModels.loaded(), local).model();
        return TestModels.remoteBranchesRead(
                        model,
                        List.of(
                                remote(ORIGIN, "HEAD"),
                                remote(ORIGIN, "next"),
                                remote(ORIGIN, "main"),
                                remote(ORIGIN, "feature"),
                                remote(UPSTREAM, "elsewhere")))
                .model()
                .withPart(
                        ForgeHome.PART,
                        ForgeHome.NONE.deciding(Option.of(new ForgeCandidate(
                                new FeatureId("forgejo"), "Forgejo", ORIGIN, PullRequestFixtures.URL))));
    }

    private static Model model() {
        return model(List.of(following("feature", Option.of("feature")), main()));
    }

    private static Next update(Model model, ForgejoCreatePullRequestMsg msg) {
        return Update.update(model, msg);
    }

    private static ForgejoNewPullRequestPopup popup(Model model) {
        return model.popup(ForgejoNewPullRequestPopup.class).get();
    }

    /**
     * {@link #model()} with the dialog open for {@code feature}, its title read as {@code Create it}.
     */
    private static Model asked() {
        return Update.update(
                        model(),
                        new CreatePullRequestMsg.TitleRead(
                                BRANCH,
                                new ForgejoNewPullRequestPopup.Opening(BRANCH, List.of(MAIN, NEXT)),
                                Option.of("Create it")))
                .model();
    }

    @Test
    void aHeadElsewhereIsRefusedSayingWhy() {
        CreateError elsewhere = new CreateError.Elsewhere(BRANCH, new RemoteBranch(UPSTREAM, BRANCH));

        Next next = update(model(), new ForgejoCreatePullRequestMsg.HeadChecked(BRANCH, Option.of(elsewhere)));

        assertEquals(Option.<AppError>of(elsewhere), next.model().outcome().error());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aPushedBranchHasTheTitleReadForTheBaseOfferedFirstTheDefaultBranch() {
        Next next = update(model(), new ForgejoCreatePullRequestMsg.HeadChecked(BRANCH, Option.none()));

        assertEquals(
                List.of(new ReadTitle(
                        BRANCH,
                        new BranchRef.Remote(new RemoteBranch(ORIGIN, MAIN)),
                        new ForgejoNewPullRequestPopup.Opening(BRANCH, List.of(MAIN, NEXT)))),
                next.cmds());
    }

    @Test
    void theBasesAreTheBranchesOfTheHomesRemoteTheParentFirstButTheOneItIsPushedTo() {
        Model withParent = model().withRepo(model().repo().withParents(HashMap.of(BRANCH, NEXT)));

        assertEquals(
                List.of(NEXT, MAIN),
                ForgejoCreatePullRequestUpdate.bases(
                        withParent, ORIGIN, withParent.repo().branch(BRANCH).get()));
    }

    @Test
    void withoutABranchOfTheHomesRemoteToMergeIntoItSaysSo() {
        Model none = model().withRepo(
                        model().repo().withRemoteBranches(new Loadable.Loaded<>(List.of(remote(ORIGIN, "feature")))));

        Next next = update(none, new ForgejoCreatePullRequestMsg.HeadChecked(BRANCH, Option.none()));

        assertEquals(
                Option.<AppError>of(new CreateError.NoBase(BRANCH)),
                next.model().outcome().error());
    }

    @Test
    void theDialogOpensWithTheTitleReadAndAnEmptyBody() {
        assertEquals(
                new ForgejoNewPullRequestPopup(BRANCH, List.of(MAIN, NEXT), MAIN, "Create it", "Create it", "", false),
                popup(asked()));
    }

    @Test
    void confirmingCreatesItFromWhereTheBranchIsPushedIntoTheBasePicked() {
        Model typed = update(asked(), new ForgejoCreatePullRequestMsg.Edited("  Create it  ", "Why", true))
                .model();

        Next next = update(typed, new ForgejoCreatePullRequestMsg.Confirmed());

        assertEquals(Option.none(), next.model().popup(ForgejoNewPullRequestPopup.class));
        assertEquals(
                List.of(new CreatePullRequest(ORIGIN, BRANCH, FEATURE, MAIN, "Create it", "Why", true)), next.cmds());
    }

    @Test
    void onceCreatedItIsTheBranchsPullRequestAtOnce() {
        Model listed = PullRequestFixtures.listed();
        CreatePullRequest cmd = new CreatePullRequest(ORIGIN, BRANCH, FEATURE, MAIN, "Create it", "", false);

        Model done = update(listed, new ForgejoCreatePullRequestMsg.Created(cmd, CREATED))
                .model();

        assertEquals(
                Option.of(CREATED),
                done.part(ForgejoPullRequests.PART)
                        .of(done.repo().branch(BRANCH).get(), ORIGIN));
    }

    @Test
    void oneOpenAlreadyIsNamedAndThePullRequestsAreReadAgain() {
        CreatePullRequest cmd = new CreatePullRequest(ORIGIN, BRANCH, FEATURE, MAIN, "Create it", "", false);

        Next next = update(model(), new ForgejoCreatePullRequestMsg.AlreadyOpen(cmd, 7));

        assertEquals(
                Option.of(new CreateFailure.AlreadyOpen(BRANCH, 7)),
                next.model().outcome().error().map(error -> ((AppError.OperationFailed) error).failure()));
        assertTrue(
                next.cmds().contains(new ReadPullRequests(ORIGIN)), next.cmds().toString());
    }
}
