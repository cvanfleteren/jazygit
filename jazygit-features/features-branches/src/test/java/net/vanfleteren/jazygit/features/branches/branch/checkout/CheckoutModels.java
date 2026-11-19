package net.vanfleteren.jazygit.features.branches.branch.checkout;

import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * Models that a checkout left behind, for the tests of the features and the app.
 */
public final class CheckoutModels {

    private CheckoutModels() {}

    /**
     * {@code model} showing that checking out {@code branch} failed, git saying {@code explanation}.
     */
    public static Model withCheckoutError(Model model, String branch, String explanation) {
        return model.fail(new AppError.OperationFailed(CheckoutCmd.CHECKOUT, Option.of(branch), refused(explanation)));
    }
}
