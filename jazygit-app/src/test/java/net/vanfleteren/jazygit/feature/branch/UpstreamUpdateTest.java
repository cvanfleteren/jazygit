package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.commit.CommitCmd;
import net.vanfleteren.jazygit.feature.commit.ResetMsg;
import net.vanfleteren.jazygit.feature.commit.ResetPopup;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The options on the upstream of the checked out branch: resetting onto it.
 */
class UpstreamUpdateTest {

    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", new Branch.Name("main"));
    private static final Branch TRACKING = new Branch(
            new Branch.Name("main"),
            true,
            new Sha("aaaa"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(ORIGIN_MAIN, 2, 1)));

    private static Model with(Branch... branches) {
        return Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(branches)))
                .model();
    }

    private static Model opened(Model model) {
        return Update.update(model, new UpstreamMsg.Requested()).model();
    }

    @Test
    void requestOpensTheOptionsForTheCheckedOutBranch() {
        Next next = Update.update(with(TRACKING), new UpstreamMsg.Requested());

        assertEquals(
                Option.of(new UpstreamPopup(Option.of(TRACKING))), next.model().popup(UpstreamPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void resettingAsksHowWarningAboutTheCommitsLeftBehind() {
        Next next = Update.update(opened(with(TRACKING)), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(
                Option.of(new ResetPopup(new ResetTarget.ToUpstream(ORIGIN_MAIN), 2)),
                next.model().popup(ResetPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theResetGoesToTheUpstream() {
        Model asking = Update.update(opened(with(TRACKING)), new UpstreamMsg.ResetChosen())
                .model();

        Next next = Update.update(asking, new ResetMsg.Chosen(ResetMode.SOFT));

        assertEquals(
                List.of(new CommitCmd.Reset(new ResetTarget.ToUpstream(ORIGIN_MAIN), ResetMode.SOFT)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aBranchWithoutUpstreamCannotBeResetOntoIt() {
        Next next = Update.update(opened(loaded()), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(Option.of("Branch main has no upstream"), errorText(next.model()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneCannotBeResetOntoIt() {
        Branch gone = new Branch(
                new Branch.Name("main"),
                true,
                new Sha("aaaa"),
                Instant.EPOCH,
                false,
                false,
                new Tracking.Gone(ORIGIN_MAIN));

        Next next = Update.update(opened(with(gone)), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(
                Option.of("Branch main has no upstream anymore: origin/main was deleted on the remote"),
                errorText(next.model()));
    }

    @Test
    void aDetachedHeadHasNoBranchToReset() {
        Model detached = Update.update(
                        with(new Branch(new Branch.Name("main"), false, new Sha("aaaa"))),
                        new LoadMsg.StatusLoaded(new RepoStatus(new Head.Detached(new Sha("aaaa")), List.of())))
                .model();
        Model asking = opened(detached);
        assertEquals(Option.of(new UpstreamPopup(Option.none())), asking.popup(UpstreamPopup.class));

        Next next = Update.update(asking, new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(Option.of("No branch is checked out"), errorText(next.model()));
    }

    @Test
    void cancellingClosesThePopup() {
        Next next = Update.update(opened(with(TRACKING)), new UpstreamMsg.Cancelled());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }
}
