package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * What the user does in the GitHub section of the settings panel, and what is read for it.
 */
public sealed interface GitHubSettingsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return GitHubSettingsUpdate.update(model, this);
    }

    /**
     * How gh is set up for the repository, as read for the settings.
     */
    record GitHubRead(GitHubSetup gitHub) implements GitHubSettingsMsg {}

    record GhExecutableRequested() implements GitHubSettingsMsg {}

    /**
     * The user confirmed the gh executable typed in the popup; blank for the one on the PATH.
     */
    record GhExecutableEntered() implements GitHubSettingsMsg {}

    /**
     * Whether {@code executable}, none for the one on the PATH, is a gh that runs, by its version.
     */
    record GhExecutableChecked(Option<String> executable, Either<Failure, String> version)
            implements GitHubSettingsMsg {}

    record AccountRequested() implements GitHubSettingsMsg {}

    /**
     * The user asked to set the browser of the account the repository uses (ADR 88).
     */
    record BrowserRequested() implements GitHubSettingsMsg {}

    /**
     * The user confirmed the browser typed in the popup; blank for none.
     */
    record BrowserEntered() implements GitHubSettingsMsg {}

    /**
     * The user chose the account the repository uses; none for gh's active one.
     */
    record AccountChosen(Option<String> user) implements GitHubSettingsMsg {}

    /**
     * The repository uses the account chosen now, and is shown again with it.
     */
    record AccountSet() implements GitHubSettingsMsg {}

    record AccountNotSet(Failure failure) implements GitHubSettingsMsg {}
}
