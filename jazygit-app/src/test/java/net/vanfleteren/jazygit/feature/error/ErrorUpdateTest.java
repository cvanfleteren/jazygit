package net.vanfleteren.jazygit.feature.error;

import static net.vanfleteren.jazygit.state.TestModels.failed;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.feature.branch.push.SetUpstreamPopup;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Errors open a popup, which closing leaves the error in the command log.
 */
class ErrorUpdateTest {

    @Test
    void aFailedOperationOpensTheErrorPopup() {
        Model failed = Update.update(loaded(), failed(new PullCmd.Pull(new Branch.Name("main")), "boom"))
                .model();

        assertEquals(failed.outcome().error().map(ErrorPopup::new), failed.popup());
    }

    @Test
    void aRefusedRequestOpensTheErrorPopup() {
        Model failed = loaded().fail(new AppError.NothingToCommit());

        assertEquals(Option.of(new ErrorPopup(new AppError.NothingToCommit())), failed.popup());
    }

    @Test
    void anOpenPopupIsNotReplacedByTheErrorPopup() {
        Model failed = loaded().openPopup(new SetUpstreamPopup(new Branch.Name("feature")))
                .fail(new AppError.InvalidUpstream("x"));

        assertEquals(Option.of(new SetUpstreamPopup(new Branch.Name("feature"))), failed.popup());
        assertEquals(
                Option.of(new AppError.InvalidUpstream("x")), failed.outcome().error());
    }

    @Test
    void dismissingClosesThePopupAndKeepsTheErrorInTheCommandLog() {
        Model failed = loaded().fail(new AppError.NothingToCommit());

        Model dismissed = Update.update(failed, new ErrorMsg.Dismissed()).model();

        assertEquals(Option.none(), dismissed.popup());
        assertEquals(
                Option.of(new AppError.NothingToCommit()), dismissed.outcome().error());
    }
}
