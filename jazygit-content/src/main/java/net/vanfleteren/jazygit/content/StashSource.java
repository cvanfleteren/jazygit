package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.ui.content.ContentArea;
import net.vanfleteren.jazygit.ui.content.ContentSource;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.ShownChanges;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.stash.StashPanel;

/**
 * What the highlighted stash stashed; the stash shown before stays while it is read. Without stashes, it says there
 * are none.
 */
record StashSource() implements ContentSource {

    /**
     * What the stash highlighted in the stash pane stashed.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.STASH, StashPanel.PANE);

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
                                Shows.whenLoaded(title, detail.value(), changes -> stashArea(model, stash, changes))))
                .getOrElse(() -> Shows.whenLoaded(
                        title,
                        model.repo().stashes(),
                        stashes -> stashes.isEmpty()
                                ? Shows.dim(title, Messages.get("content.noStashes"))
                                : Shows.loading(title)));
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
