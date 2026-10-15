package net.vanfleteren.jazygit.feature.repos;

import io.vavr.control.Option;
import java.io.File;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * Popup asking which recent repository to switch to. The directories in the home directory are shown from
 * {@code ~}.
 */
public final class RecentReposDialog {

    public static final PickDialog.Spec<RecentReposPopup> SPEC = new PickDialog.Spec<>(
            "recent-repos",
            RecentReposPopup.class,
            popup -> Messages.get("dialog.recentRepos.title"),
            popup -> popup.directories().map(RecentReposDialog::shown),
            shown -> new RecentReposMsg.Chosen(directory(shown)),
            RecentReposMsg.Cancelled::new,
            Option.of("dialog.recentRepos.none"));
    private static final String HOME = System.getProperty("user.home");

    private RecentReposDialog() {}

    /**
     * How {@code directory} is shown: from {@code ~} when it is in the home directory.
     */
    static String shown(String directory) {
        return directory.startsWith(HOME + File.separator) ? "~" + directory.substring(HOME.length()) : directory;
    }

    /**
     * The directory shown as {@code shown}. A directory that is not shown from {@code ~} is absolute, so it never
     * starts with one.
     */
    static String directory(String shown) {
        return shown.startsWith("~") ? HOME + shown.substring(1) : shown;
    }
}
