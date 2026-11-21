package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The commit highlighted in the commits pane.
 */
record CommitSource() implements ContentSource {

    /**
     * The commit highlighted in the commits pane.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.COMMIT, PaneId.COMMITS);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(
                pane.equals(Mirrored.COMMITS),
                () -> commit(
                        model,
                        AREA,
                        model.repoView().preview().commitDetail(),
                        model::commit,
                        Option.when(model.repo().commits().toOption().exists(List::isEmpty), "content.noCommits")));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    /**
     * The commit {@code detail} is of, found by {@code commit}, in {@code area} once what it changed is read, as the
     * commits pane shows its commits; the one shown before stays while it is read. Before one is highlighted, the
     * message {@code nothing} says there is nothing to highlight, if it does.
     */
    static Shown commit(
            Model model,
            ContentArea area,
            Option<Preview<Sha, DiffText>> detail,
            Function<Sha, Option<Commit>> commit,
            Option<String> nothing) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("content.panel.commit.title");
        DiffLook look = model.preferences().diffLook();
        return detail.map(Preview::shown)
                .map(shown -> Shown.whenLoaded(
                        title,
                        shown.value(),
                        changes -> commit.apply(shown.key())
                                .<Shown>map(found -> commitArea(area, found, changes, options, look))
                                .getOrElse(() -> Shown.loading(title))))
                .getOrElse(() -> nothing.map(message -> Shown.dim(title, Messages.get(message)))
                        .getOrElse(() -> Shown.loading(title)));
    }

    private static Shown.Area commitArea(
            ContentArea area, Commit commit, DiffText changes, DiffOptions options, DiffLook look) {
        return new Shown.Area(
                area,
                Shows.titled(Messages.get("content.panel.commit.titleFor", commit.shortSha()), options),
                new ScrolledContent.CommitChanges(commit, changes),
                new CommitBody(commit, new ShownChanges(Shows.shownChanges(changes, options), look)));
    }
}
