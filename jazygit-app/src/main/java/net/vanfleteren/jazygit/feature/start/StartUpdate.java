package net.vanfleteren.jazygit.feature.start;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.repos.RecentRepo;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Asking what to open instead, when jazygit starts outside a repository: making the folder one, another folder, or
 * a recent repository (ADR 28). The model has no repository meanwhile; only its popup and preferences are used.
 */
public final class StartUpdate {

    private StartUpdate() {}

    /**
     * Asking what to do, as {@code directory} is in no repository, drawn as {@code settings} and {@code themeOverride}
     * say.
     *
     * @param exists whether {@code directory} is a folder at all
     * @param recent the recent repositories that can be opened instead
     */
    public static Model init(
            String directory,
            boolean exists,
            List<RecentRepo> recent,
            Settings settings,
            Option<ThemeName> themeOverride) {
        return Model.initial("")
                .mapPreferences(
                        preferences -> preferences.withSettings(settings).withThemeOverride(themeOverride))
                .openPopup(new NoRepositoryPopup(directory, exists, recent, Option.none()));
    }

    public static Next update(Model model, StartMsg msg) {
        return switch (msg) {
            case StartMsg.InitChosen() ->
                new Next(
                        model,
                        model.popup(NoRepositoryPopup.class)
                                .filter(NoRepositoryPopup::exists)
                                .<Cmd>map(popup -> new StartCmd.Init(popup.directory()))
                                .toList());
            case StartMsg.OpenFolderChosen() -> open(model, NoRepositoryPopup.class, OpenFolderPopup::new);
            case StartMsg.OpenRecentChosen() ->
                Next.of(model.popup(NoRepositoryPopup.class)
                        .filter(popup -> popup.recent().nonEmpty())
                        .map(popup -> model.openPopup(new OpenRecentPopup(popup)))
                        .getOrElse(model));
            case StartMsg.FolderConfirmed() ->
                new Next(
                        model,
                        model.popup(OpenFolderPopup.class)
                                .filter(popup -> !popup.text().isBlank())
                                .<Cmd>map(popup -> new StartCmd.Open(
                                        popup.outside().directory(),
                                        popup.text().strip()))
                                .toList());
            case StartMsg.FolderCancelled() -> open(model, OpenFolderPopup.class, OpenFolderPopup::outside);
            case StartMsg.RecentPicked(String directory) ->
                new Next(
                        model,
                        model.popup(OpenRecentPopup.class)
                                .<Cmd>map(popup -> new StartCmd.Open(directory, directory))
                                .toList());
            case StartMsg.RecentCancelled() -> open(model, OpenRecentPopup.class, OpenRecentPopup::outside);
            // The app stops asking, and shows the repository.
            case StartMsg.Opened(String worktree) -> Next.of(model);
            // A path typed that names no folder is said under it, so it can be corrected.
            case StartMsg.NoRepository(String directory, boolean exists)
            when !exists && model.popup(OpenFolderPopup.class).isDefined() ->
                open(model, OpenFolderPopup.class, popup -> popup.withNotFound(directory));
            case StartMsg.NoRepository(String directory, boolean exists) ->
                Next.of(outside(model)
                        .map(popup -> model.openPopup(popup.about(directory, exists)))
                        .getOrElse(model));
            // So is a path git refuses to open, like that of a bare repository.
            case StartMsg.Failed(var failure)
            when model.popup(OpenFolderPopup.class).isDefined() ->
                open(model, OpenFolderPopup.class, popup -> popup.failing(failure));
            case StartMsg.Failed(var failure) ->
                Next.of(outside(model)
                        .map(popup -> model.openPopup(popup.failing(failure)))
                        .getOrElse(model));
        };
    }

    /**
     * {@code model} with the popup {@code next} makes of the open {@code P} open instead, if a {@code P} is open.
     */
    private static <P extends Popup> Next open(Model model, Class<P> type, Function<P, Popup> next) {
        return Next.of(model.popup(type).map(next).map(model::openPopup).getOrElse(model));
    }

    /**
     * What the open popup goes back to, or is: the folder in no repository, and what can be opened instead.
     */
    private static Option<NoRepositoryPopup> outside(Model model) {
        return model.popup(NoRepositoryPopup.class)
                .orElse(() -> model.popup(OpenFolderPopup.class).map(OpenFolderPopup::outside))
                .orElse(() -> model.popup(OpenRecentPopup.class).map(OpenRecentPopup::outside));
    }
}
