package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.Stash;
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
 * What the highlighted stash stashed; the stash shown before stays while it is read. Without stashes, it says there
 * are none.
 */
record StashSource() implements ContentSource {

    /**
     * What the stash highlighted in the stash pane stashed.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.STASH, PaneId.STASH);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane == Mirrored.STASH, () -> stash(model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    private static Shown stash(Model model) {
        String title = Messages.get("panel.stash.title");
        return model.repoView()
                .preview()
                .stashDetail()
                .map(Preview::shown)
                .flatMap(detail -> model.repo()
                        .stash(detail.key())
                        .map(stash ->
                                Shown.whenLoaded(title, detail.value(), changes -> stashArea(model, stash, changes))))
                .getOrElse(() -> Shown.whenLoaded(
                        title,
                        model.repo().stashes(),
                        stashes -> stashes.isEmpty()
                                ? Shown.dim(title, Messages.get("content.noStashes"))
                                : Shown.loading(title)));
    }

    private static Shown stashArea(Model model, Stash stash, DiffText changes) {
        DiffOptions options = model.preferences().diffOptions();
        return new Shown.Area(
                AREA,
                Shows.titled(Messages.get("content.panel.stash.titleFor", stash.ref()), options),
                new ScrolledContent.StashChanges(stash.sha(), changes),
                new StashBody(
                        stash,
                        new ShownChanges(
                                Shows.shownChanges(changes, options),
                                model.preferences().diffLook())));
    }
}
