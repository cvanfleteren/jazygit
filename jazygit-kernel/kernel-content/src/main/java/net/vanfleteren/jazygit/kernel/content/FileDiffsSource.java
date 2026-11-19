package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Diffs;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * What the node highlighted in the files of the working tree changed: one area for the staged changes and one for the
 * unstaged changes, one above the other when both exist.
 */
record FileDiffsSource() implements ContentSource {

    /**
     * The staged changes of the node highlighted in the files pane.
     */
    static final ContentArea STAGED = ContentArea.of(AreaId.STAGED, FilesPanel.PANE);

    /**
     * The unstaged changes of the node highlighted in the files pane.
     */
    static final ContentArea UNSTAGED = ContentArea.of(AreaId.UNSTAGED, FilesPanel.PANE);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane == Mirrored.FILES, () -> FileNote.noted(fileDiffs(model)));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(STAGED, UNSTAGED, FileNote.AREA);
    }

    private static Shown fileDiffs(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Shows.titled(Messages.get("panel.diff.title"), options);
        Option<RepoStatus> status = model.repo().status().toOption();
        // With no files there is nothing to highlight, so no diff is ever requested.
        if (status.exists(read -> read.files().isEmpty())) {
            return Shown.dim(title, Messages.get("content.noChangedFiles"));
        }
        if (status.exists(read -> FilesPanel.noneMatch(model, read.files()))) {
            return Shown.dim(title, Messages.get("panel.files.noMatches"));
        }
        return model.repoView()
                .preview()
                .fileDiff()
                .map(Preview::shown)
                .map(diff -> Shown.whenLoaded(
                        title,
                        diff.value(),
                        diffs -> diffAreas(options, model.preferences().diffLook(), title, diffs)))
                .getOrElse(() -> Shown.loading(title));
    }

    /**
     * The staged and the unstaged changes of {@code diffs}, each drawn as {@code look} says on its own (ADR 47).
     */
    private static Shown diffAreas(DiffOptions options, DiffLook look, String title, Diffs diffs) {
        if (diffs.isEmpty()) {
            return Shown.dim(
                    title, Messages.get(options.ignoreWhitespace() ? "content.onlyWhitespace" : "content.noChanges"));
        }
        return new Shown.Stacked(List.of(
                        new DiffArea(STAGED, "content.staged", diffs.staged()),
                        new DiffArea(UNSTAGED, "content.unstaged", diffs.unstaged()))
                .filter(area -> !Shows.shown(area.diff()).isBlank())
                .map(area -> new Shown.Area(
                        area.area(),
                        Shows.titled(Messages.get(area.titleKey()), options),
                        new ScrolledContent.Diff(area.diff()),
                        new DiffBody(new ShownChanges(Shows.shown(area.diff()), look)))));
    }

    /**
     * One of the areas {@link #diffAreas} draws: the {@code area}, the key of its title and its {@code diff}.
     */
    private record DiffArea(ContentArea area, String titleKey, DiffText diff) {}
}
