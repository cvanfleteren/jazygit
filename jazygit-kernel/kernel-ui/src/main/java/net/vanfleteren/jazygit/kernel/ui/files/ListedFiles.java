package net.vanfleteren.jazygit.kernel.ui.files;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Pathed;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileFilter;
import net.vanfleteren.jazygit.kernel.state.view.FileFilterUpdate;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ListFilter;

/**
 * What the files pane, which lives in a group of features (ADR 158), and the content panel both say of the files
 * listed: what they are of, which of them the filter keeps (ADR 105), and why none is listed when only some are
 * (ADR 57).
 */
public final class ListedFiles {

    private ListedFiles() {}

    /**
     * How the commit, the stash, the pull request or the branch {@code of} is named: by its short sha, by its
     * {@code stash@{n}}, by its {@code #n}, by its name and the base it is compared with (ADR 61), or the main branch
     * by its name and the commit it is at (ADR 128): what it shows is that commit, not what the branch changed.
     */
    public static String changesName(Model model, Changes of) {
        return switch (of) {
            case Changes.OfCommit(Sha sha) -> sha.shortSha();
            case Changes.OfTip(var branch, Sha sha) ->
                Messages.get("panel.commitFiles.tip", branch.value(), sha.shortSha());
            case Changes.OfStash(Sha sha) ->
                model.repo().stash(sha).map(Stash::ref).getOrElse(sha::shortSha);
            case Changes.OfPullRequest(var commits, var _) ->
                "#" + commits.number().value();
            case Changes.OfBranch(var branch, var base, var sha) ->
                Messages.get(
                        "panel.commitFiles.branch", branch.value(), base.ref().name());
        };
    }

    /**
     * Why none of the files of a pull request is listed, when only some of them are.
     */
    public static Option<String> nothingListed(CommitFilesView.Listed listed) {
        return switch (listed) {
            case ALL -> Option.none();
            case UNRESOLVED -> Option.of(Messages.get("panel.commitFiles.noneUnresolved"));
            case REMARKED -> Option.of(Messages.get("panel.commitFiles.noneRemarked"));
        };
    }

    /**
     * Whether the files pane, showing {@code files}, shows none of them for its filter: the content panel then says
     * so too, in place of the diff of a node no longer highlighted (ADR 105).
     */
    public static <T extends Pathed> boolean noneMatch(Model model, List<T> files) {
        Option<String> shown = FileFilter.shown(FileFilterUpdate.shown(model));
        return shown.isDefined() && !files.isEmpty() && matching(files, shown).isEmpty();
    }

    /**
     * Those of {@code files} whose path, or the path a renamed one came from, contains {@code filter}: all of them
     * when there is none.
     */
    public static <T extends Pathed> List<T> matching(List<T> files, Option<String> filter) {
        return filter.map(typed -> files.filter(file -> ListFilter.matches(
                                Option.of(typed), file.path().value())
                        || file.origin().exists(origin -> ListFilter.matches(Option.of(typed), origin.value()))))
                .getOrElse(files);
    }
}
