package net.vanfleteren.jazygit.feature.branch.rename;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.localBranchesRead;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubBranches;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubRemotes;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationMsg;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Renaming a branch that follows a branch on a remote, which is asked whether to rename that too (ADR 90).
 */
class RenameBranchTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName TOPIC = new BranchName("topic");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);

    /**
     * The model with main checked out, the default branch, and feature, which follows {@code upstream} when given;
     * both are on the remote.
     */
    private static Model following(Option<RemoteBranch> upstream) {
        return localBranchesRead(loaded(), branches(upstream)).model();
    }

    private static List<Branch> branches(Option<RemoteBranch> upstream) {
        return List.of(
                Fixtures.branch(
                        MAIN,
                        true,
                        new Sha("aaaa"),
                        Instant.EPOCH,
                        true,
                        true,
                        Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", MAIN)), 0, 0))),
                Fixtures.branch(
                        FEATURE,
                        false,
                        new Sha("ffff"),
                        Instant.EPOCH,
                        upstream.isDefined(),
                        false,
                        upstream.map(remote -> new Upstream(new BranchRef.Remote(remote), 0, 0))));
    }

    /**
     * {@code model} with {@code branch} to be renamed to {@code name}, confirmed.
     */
    private static Next renamed(Model model, BranchName branch, String name) {
        Model open = Update.update(model, new RenameBranchMsg.Requested(branch)).model();
        return Update.update(typed(open, name), new RenameBranchMsg.Confirmed());
    }

    @Test
    void aBranchFollowingABranchOfItsNameIsAskedWhetherToRenameThatToo() {
        Next asked = renamed(following(Option.of(ORIGIN_FEATURE)), FEATURE, " topic ");

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new RenameUpstreamPopup(FEATURE, TOPIC, ORIGIN_FEATURE)),
                asked.model().popup());
    }

    @Test
    void renamingOnlyLocallyLeavesTheUpstream() {
        Model asked =
                renamed(following(Option.of(ORIGIN_FEATURE)), FEATURE, "topic").model();

        Next next = Update.update(asked, new RenameBranchMsg.LocallyChosen());

        assertEquals(List.of(new RenameBranchCmd(FEATURE, TOPIC)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void renamingOnTheRemoteTooRenamesTheUpstream() {
        Model asked =
                renamed(following(Option.of(ORIGIN_FEATURE)), FEATURE, "topic").model();

        Next next = Update.update(asked, new RenameBranchMsg.WithUpstreamChosen());

        assertEquals(List.of(new RenameBranchCmd(FEATURE, TOPIC, Option.of(ORIGIN_FEATURE))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void anUpstreamThatChangedMeanwhileIsNotRenamedAndTheDialogClosesSayingSo() {
        Model asked =
                renamed(following(Option.of(ORIGIN_FEATURE)), FEATURE, "topic").model();
        Model changed = localBranchesRead(asked, branches(Option.none())).model();

        Next next = Update.update(changed, new RenameBranchMsg.WithUpstreamChosen());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(RenameUpstreamPopup.class));
        assertEquals(
                Option.of("Nothing was renamed: feature no longer follows origin/feature on the remote, as it did when"
                        + " asked. Rename it again to see what it follows now"),
                errorText(next.model()));
    }

    @Test
    void escapeCancelsTheWholeRename() {
        Model asked =
                renamed(following(Option.of(ORIGIN_FEATURE)), FEATURE, "topic").model();

        Next cancelled = Update.update(asked, new PopupMsg.Dismissed(RenameUpstreamPopup.class));

        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void aBranchWithoutAnUpstreamOfItsNameOnARemoteIsRenamedWithoutAsking() {
        RemoteBranch develop = new RemoteBranch("origin", new BranchName("develop"));

        assertEquals(
                List.of(new RenameBranchCmd(FEATURE, TOPIC)),
                renamed(following(Option.none()), FEATURE, "topic").cmds());
        assertEquals(
                List.of(new RenameBranchCmd(FEATURE, TOPIC)),
                renamed(following(Option.of(develop)), FEATURE, "topic").cmds());
    }

    @Test
    void theDefaultBranchIsRenamedWithoutAskingAsItsUpstreamIsNotRenamed() {
        assertEquals(
                List.of(new RenameBranchCmd(MAIN, TOPIC)),
                renamed(following(Option.of(ORIGIN_FEATURE)), MAIN, "topic").cmds());
    }

    @Test
    void theLocalBranchIsRenamedBeforeItsUpstreamWhichItThenFollows() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        Git git = StubGit.of()
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> rename(BranchName name, BranchName newName) {
                        ran.add("rename " + name.value() + " " + newName.value());
                        return StubGit.ran("git branch --move -- feature topic");
                    }
                })
                .withRemotes(new StubRemotes() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> renameUpstream(
                            BranchName branch, RemoteBranch upstream) {
                        ran.add("rename " + upstream.shortName() + " for " + branch.value());
                        return StubGit.ran("git push --atomic origin");
                    }
                });
        RenameBranchCmd cmd = new RenameBranchCmd(FEATURE, TOPIC, Option.of(ORIGIN_FEATURE));

        Msg done = cmd.run(new Env(git, StubGitHub.of()));

        assertEquals(java.util.List.of("rename feature topic", "rename origin/feature for topic"), ran);
        assertEquals(
                2,
                assertInstanceOf(OperationMsg.Succeeded.class, done).commands().size());
        assertEquals(Option.of(Cmd.Guard.REMOTE_REFS), cmd.guard());
        assertEquals(Option.none(), new RenameBranchCmd(FEATURE, TOPIC).guard());
    }

    @Test
    void anUpstreamThatWasNotRenamedSaysTheBranchWasAndHowToRenameItThere() {
        GitError cause = new GitError.CommandFailed("stale info", 1);
        Model failed = Update.update(
                        loaded(),
                        new OperationMsg.Failed(
                                new RenameBranchCmd(FEATURE, TOPIC, Option.of(ORIGIN_FEATURE)),
                                new Failure.Git(new GitError.UpstreamNotRenamed(ORIGIN_FEATURE, TOPIC, cause))))
                .model();

        assertEquals(
                Option.of("Renaming branch feature failed: it is now topic, but origin/feature was not renamed on the"
                        + " remote and topic still follows it: stale info. To rename it there yourself: git push"
                        + " --atomic origin refs/remotes/origin/feature:refs/heads/topic :refs/heads/feature, then"
                        + " git config branch.topic.merge refs/heads/topic"),
                errorText(failed));
    }

    @Test
    void anUpstreamNoLongerOnTheRemoteSaysThereWasNothingToRenameThere() {
        Model failed = Update.update(
                        loaded(),
                        new OperationMsg.Failed(
                                new RenameBranchCmd(FEATURE, TOPIC, Option.of(ORIGIN_FEATURE)),
                                new Failure.Git(new GitError.UpstreamNotOnRemote(ORIGIN_FEATURE, TOPIC))))
                .model();

        assertEquals(
                Option.of("Renaming branch feature failed: it is now topic, but origin/feature is no longer on the"
                        + " remote, so there was nothing to rename there; topic still follows it"),
                errorText(failed));
    }
}
