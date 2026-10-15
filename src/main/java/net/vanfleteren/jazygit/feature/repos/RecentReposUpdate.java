package net.vanfleteren.jazygit.feature.repos;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching to a recent repository, which the app then shows as if it had been started there.
 */
public final class RecentReposUpdate {

    private RecentReposUpdate() {
    }

    public static Next update(Model model, RecentReposMsg msg) {
        return switch (msg) {
            // The key works everywhere, but does not take away a dialog that is open.
            case RecentReposMsg.Requested() -> model.popup().isDefined()
                    ? Next.of(model)
                    : Next.of(model, new RecentReposCmd.ListRecent());
            case RecentReposMsg.Listed(var directories) ->
                    Next.of(model.openPopupIfFree(new RecentReposPopup(directories)));
            case RecentReposMsg.Cancelled() -> Next.of(model.withoutPopup(RecentReposPopup.class));
            case RecentReposMsg.Chosen(String directory) -> model.popup(RecentReposPopup.class).isDefined()
                    ? Next.of(model.withoutPopup(RecentReposPopup.class).clearError(),
                    new RecentReposCmd.SwitchTo(directory))
                    : Next.of(model);
            // The app shows the other repository already; this one is not shown anymore.
            case RecentReposMsg.Switched(String directory) -> Next.of(model);
            case RecentReposMsg.NotSwitched(String directory, var failure) ->
                    Next.of(model.fail(new AppError.RepositoryNotOpened(directory, failure)));
        };
    }
}
