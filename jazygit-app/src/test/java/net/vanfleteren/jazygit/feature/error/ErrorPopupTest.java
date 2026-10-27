package net.vanfleteren.jazygit.feature.error;

import static net.vanfleteren.jazygit.state.TestModels.failed;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.feature.branch.push.SetUpstreamPopup;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Errors open a popup, which closing leaves the error in the command log.
 */
class ErrorPopupTest {

    @Test
    void aFailedOperationOpensTheErrorPopup() {
        Model failed = Update.update(loaded(), failed(new PullCmd.Pull(new BranchName("main")), "boom"))
                .model();

        assertEquals(failed.outcome().error().map(ErrorPopup::new), failed.popup());
    }

    @Test
    void aRefusedRequestOpensTheErrorPopup() {
        Model failed = loaded().fail(AppError.refused("error.commit.nothing"));

        assertEquals(Option.of(new ErrorPopup(AppError.refused("error.commit.nothing"))), failed.popup());
    }

    @Test
    void anOpenPopupIsNotReplacedByTheErrorPopup() {
        Model failed = loaded().openPopup(new SetUpstreamPopup(new BranchName("feature")))
                .fail(AppError.refused("error.upstream.invalid", "x"));

        assertEquals(Option.of(new SetUpstreamPopup(new BranchName("feature"))), failed.popup());
        assertEquals(
                Option.of(AppError.refused("error.upstream.invalid", "x")),
                failed.outcome().error());
    }

    @Test
    void dismissingClosesThePopupAndKeepsTheErrorInTheCommandLog() {
        Model failed = loaded().fail(AppError.refused("error.commit.nothing"));

        Model dismissed =
                Update.update(failed, new Msg.PopupDismissed(ErrorPopup.class)).model();

        assertEquals(Option.none(), dismissed.popup());
        assertEquals(
                Option.of(AppError.refused("error.commit.nothing")),
                dismissed.outcome().error());
    }
}
