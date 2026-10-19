package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface SettingsPanelMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SettingsPanelUpdate.update(model, this);
    }

    /**
     * The user asked to see the settings.
     */
    record Requested() implements SettingsPanelMsg {}

    /**
     * How gh is set up for the repository, as read for the settings.
     */
    record GitHubRead(GitHubSetup gitHub) implements SettingsPanelMsg {}

    record Closed() implements SettingsPanelMsg {}

    record ThemeRequested() implements SettingsPanelMsg {}

    record GhExecutableRequested() implements SettingsPanelMsg {}

    record GhExecutableCancelled() implements SettingsPanelMsg {}

    /**
     * The user confirmed the gh executable typed in the popup; blank for the one on the PATH.
     */
    record GhExecutableEntered() implements SettingsPanelMsg {}

    /**
     * Whether {@code executable}, none for the one on the PATH, is a gh that runs, by its version.
     */
    record GhExecutableChecked(Option<String> executable, Either<Failure, String> version)
            implements SettingsPanelMsg {}

    record AccountRequested() implements SettingsPanelMsg {}

    record AccountCancelled() implements SettingsPanelMsg {}

    /**
     * The user chose the account the repository uses; none for gh's active one.
     */
    record AccountChosen(Option<String> user) implements SettingsPanelMsg {}

    /**
     * The repository uses the account chosen now, and is shown again with it.
     */
    record AccountSet() implements SettingsPanelMsg {}

    record AccountNotSet(Failure failure) implements SettingsPanelMsg {}

    /**
     * The repository is shown again, with the settings changed.
     */
    record Reopened() implements SettingsPanelMsg {}

    record NotReopened(Failure failure) implements SettingsPanelMsg {}
}
