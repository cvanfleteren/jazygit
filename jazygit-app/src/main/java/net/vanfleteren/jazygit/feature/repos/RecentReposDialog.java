package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;

/**
 * Popup asking which recent repository to switch to, showing for each its name, its checked out branch and the
 * directory it is in. The directories in the home directory are shown from {@code ~}.
 */
public final class RecentReposDialog {

    public static final PickDialog.Spec<RecentReposPopup, String> SPEC = new PickDialog.Spec<>(
            "recent-repos",
            RecentReposPopup.class,
            popup -> Messages.get("dialog.recentRepos.title"),
            popup -> popup.repos().map(RecentReposDialog::row),
            RecentReposMsg.Chosen::new,
            RecentReposMsg.Cancelled::new,
            Option.of("dialog.recentRepos.none"));
    // The characters of a name or a branch shown at most, so that long ones do not push the rest away.
    private static final int CAP = 30;
    private static final String HOME = System.getProperty("user.home");

    private RecentReposDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningToFocus(new PickDialog<>(SPEC, dispatch)));
    }

    static PickDialog.Row<String> row(RecentRepo repo) {
        Path directory = Path.of(repo.directory());
        return new PickDialog.Row<>(
                repo.directory(),
                List.of(
                        Cell.of(repo.label()).capped(CAP),
                        repo.head()
                                .map(RecentReposDialog::head)
                                .getOrElse(() -> Cell.of(""))
                                .capped(CAP),
                        Cell.styled(
                                Option.of(directory.getParent())
                                        .map(parent -> shown(parent.toString()))
                                        .getOrElse(""),
                                Theme.Classes.REPO_LOCATION)));
    }

    private static Cell head(Head head) {
        return switch (head) {
            case Head.OnBranch(Branch.Name branch, Option<Sha> tip) ->
                Cell.styled(branch.value(), Theme.Classes.REPO_BRANCH);
            case Head.Detached(Sha at) -> Cell.styled(at.shortSha(), Theme.Classes.DIM);
        };
    }

    /**
     * How {@code directory} is shown: from {@code ~} when it is in the home directory.
     */
    static String shown(String directory) {
        return directory.equals(HOME) || directory.startsWith(HOME + File.separator)
                ? "~" + directory.substring(HOME.length())
                : directory;
    }
}
