package net.vanfleteren.jazygit.feature.github.checkout;

import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg.Requested;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Checking out a pull request listed on the pull requests screen.
 */
public final class CheckoutPullRequestUpdate {

    private CheckoutPullRequestUpdate() {}

    public static Next update(Model model, CheckoutPullRequestMsg msg) {
        return switch (msg) {
            case Requested(PullRequestSummary pullRequest) -> checkout(model, pullRequest);
        };
    }

    /**
     * Checks out {@code pullRequest} in place, if it is of the repository and the working tree has no changes to
     * bring along; untracked files do not count, as git only stops for those that are in the way. Until it is
     * known which repository this is, the user is told why it is not known yet.
     */
    private static Next checkout(Model model, PullRequestSummary pullRequest) {
        String name = pullRequest.repository() + "#" + pullRequest.number();
        boolean changed = Update.files(model).exists(file -> file.type() != ChangeType.UNTRACKED);
        return model.prOverview()
                .ofThisRepository(pullRequest)
                .fold(
                        unknown -> Next.of(model.fail(unknown)),
                        ofThis -> !ofThis
                                ? Next.of(model.fail(new AppError.OtherRepository(name)))
                                : changed
                                        ? Next.of(model.fail(new AppError.ChangesBeforeCheckout(name)))
                                        : Update.start(model.clearError(), new CheckoutPullRequestCmd(pullRequest)));
    }
}
