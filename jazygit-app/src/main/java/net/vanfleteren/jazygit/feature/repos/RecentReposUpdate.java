package net.vanfleteren.jazygit.feature.repos;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching to a recent repository, which the app then shows as if it had been started there.
 */
public final class RecentReposUpdate {

    private RecentReposUpdate() {}

    public static Next update(Model model, RecentReposMsg msg) {
        return switch (msg) {
            // The key works everywhere, but does not take away a dialog that is open.
            case RecentReposMsg.Requested() when model.popup().isDefined() -> Next.of(model);
            case RecentReposMsg.Requested() -> Next.of(model, new RecentReposCmd.ListRecent());
            case RecentReposMsg.Listed(var repos) -> Next.of(model.openPopupIfFree(new RecentReposPopup(repos)));
            case RecentReposMsg.Chosen(String directory)
            when model.popup(RecentReposPopup.class).isDefined() ->
                Next.of(
                        model.withoutPopup(RecentReposPopup.class).clearError(),
                        // It starts out as the app does.
                        new RecentReposCmd.SwitchTo(directory, Landing.START));
            case RecentReposMsg.Chosen(String directory) -> Next.of(model);
            // The app shows the other repository already; this one is not shown anymore.
            case RecentReposMsg.Switched(String directory) -> Next.of(model);
            case RecentReposMsg.NotSwitched(String directory, var failure) ->
                Next.of(model.fail(new AppError.RepositoryNotOpened(directory, failure)));
        };
    }
}
