package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.AccountBrowsers;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;

/**
 * The settings GitHub keeps in the settings file (ADR 163): the gh executable to run, and the browser of each account.
 */
public final class GitHubSettings {

    /**
     * The gh executable to run, if not the one on the PATH; one that is blank is not valid.
     */
    public static final SettingKey<Option<String>> GH_EXECUTABLE = SettingKey.of(
                    "github.executable",
                    Option.none(),
                    Codec.text(path -> Option.when(!path.isBlank(), path), path -> path)
                            .optional())
            // The GitHub client runs it, which a repository gets as it is shown.
            .withReopens(true);

    /**
     * The browser of each GitHub account (ADR 88): a table of tables, by host and then by login.
     */
    public static final SettingKey<AccountBrowsers> BROWSERS = SettingKey.of(
            "github.browsers",
            AccountBrowsers.NONE,
            Codec.of(
                    Codec.whole(AccountBrowsers::read, browsers -> browsers)::decode,
                    (key, browsers) -> browsers.entries()));

    private GitHubSettings() {}

    /**
     * The gh executable to run for {@code settings}: the one chosen, else the one on the PATH.
     */
    public static String gh(Settings settings) {
        return settings.get(GH_EXECUTABLE).getOrElse("gh");
    }
}
