package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.vanfleteren.jazygit.content.diff.ShownRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.content.ContentArea;
import net.vanfleteren.jazygit.ui.content.ContentSource;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.ShownChanges;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.i18n.Messages;

/**
 * What the commit or the stash whose files the files pane lists changed in the files of the node highlighted in them;
 * what was shown before stays while it is read. Before a node is highlighted, it is loading, or says that nothing
 * changed.
 */
record CommitFilesSource() implements ContentSource {

    /**
     * What a commit or a stash changed in the files highlighted in the tree of them the files pane lists.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.COMMIT_FILE, FilesPanel.PANE);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        if (pane != Mirrored.FILES) {
            return Option.none();
        }
        return model.repoView().files().commitFiles().map(view -> FileNote.noted(commitFiles(model, view)));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA, FileNote.AREA);
    }

    private static Shown commitFiles(Model model, CommitFilesView view) {
        DiffOptions options = model.preferences().diffOptions();
        String name = FilesPanel.changesName(model, view.of());
        String title = Shows.titled(Messages.get("panel.commitFiles.titleFor", name), options);
        // A node the commit did not change, among all its files, shows as it is in it (ADR 128).
        Option<Shown> unchanged = view.unchanged()
                .filter(node -> view.allFiles())
                .map(node -> UnchangedSource.unchanged(
                        model,
                        node,
                        unchangedTitle(name, node),
                        Option.of(view.of().sha())));
        if (unchanged.isDefined()) {
            return unchanged.get();
        }
        // When only the files with review threads are listed and none is, nothing is highlighted (ADR 57).
        Option<Shown> nothingListed = FilesPanel.nothingListed(view.listed())
                .filter(nothing -> !view.listedFiles().toOption().exists(List::nonEmpty))
                .map(nothing -> Shown.whenLoaded(title, view.listedFiles(), listed -> Shown.dim(title, nothing)))
                // Nor when the filter hides all files listed (ADR 105).
                .orElse(() -> view.listedFiles()
                        .toOption()
                        .filter(listed -> FilesPanel.noneMatch(model, listed))
                        .map(listed -> Shown.dim(title, Messages.get("panel.files.noMatches"))));
        return nothingListed.getOrElse(() -> view.diff()
                .map(Preview::shown)
                .map(diff -> Shown.whenLoaded(
                        title,
                        diff.value(),
                        changes -> diff.key().isEmpty()
                                ? Shown.dim(title, Messages.get("content.noDirectFiles"))
                                : fileChanges(model, view, name, diff.key(), changes)))
                .getOrElse(() -> Shown.whenLoaded(
                        title,
                        view.files(),
                        files -> files.isEmpty()
                                ? Shown.dim(title, Messages.get("content.noChanges"))
                                : Shown.loading(title))));
    }

    /**
     * The title of the node without changes {@code node} among the files of the commit or stash named {@code name}.
     */
    private static String unchangedTitle(String name, UnchangedNode node) {
        return switch (node) {
            case UnchangedNode.File(var content) ->
                Messages.get(
                        "content.panel.commitFile.titleFor",
                        name,
                        content.shown().key());
            case UnchangedNode.Directory _ -> Messages.get("panel.commitFiles.titleFor", name);
        };
    }

    /**
     * What the commit or the stash {@code view} lists the files of, named {@code name}, changed in its direct
     * {@code files}, {@code changes}, with the review threads of a pull request on them.
     */
    private static Shown.Area fileChanges(
            Model model, CommitFilesView view, String name, List<CommitFile> files, DiffText changes) {
        DiffOptions options = model.preferences().diffOptions();
        return new Shown.Area(
                AREA,
                Shows.titled(Messages.get("content.panel.commitFile.titleFor", name, nodePath(files)), options),
                new ScrolledContent.FileChanges(view.of(), files, changes),
                new DiffBody(
                        new ShownChanges(
                                Shows.shownChanges(changes, options),
                                model.preferences().diffLook()),
                        remarks(model, view, files)));
    }

    /**
     * The review threads of the pull request {@code view} lists the files of, as they are shown with its diff of
     * {@code files}; none for a commit or a stash, and while they are first read. The time they are told to have been
     * written against is only that of the clock when one of them is on those files: it is part of what the content
     * panel keeps its styled diff by, so it would style a diff without them again every minute.
     */
    private static Option<ShownRemarks> remarks(Model model, CommitFilesView view, List<CommitFile> files) {
        return view.remarks().flatMap(remarks -> remarks(model, view, files, remarks));
    }

    /**
     * The review threads {@code remarks} as they are shown with the diff of {@code files}, as
     * {@link #remarks(Model, CommitFilesView, List)} says.
     */
    private static Option<ShownRemarks> remarks(
            Model model, CommitFilesView view, List<CommitFile> files, Loadable<ReviewRemarks> remarks) {
        return switch (remarks) {
            case Loadable.Loaded<ReviewRemarks>(ReviewRemarks read) ->
                Option.of(new ShownRemarks(
                        read.threads(),
                        read.threads().exists(thread -> files.exists(file -> thread.isOn(file.path(), file.origin())))
                                ? model.now().truncatedTo(ChronoUnit.MINUTES)
                                : Instant.EPOCH,
                        Option.none(),
                        view.remarksAtItsCommits(),
                        view.collapsed(),
                        view.sending(),
                        view.deleting(),
                        view.pictures().filterValues(Option::isDefined).mapValues(Option::get)));
            case Loadable.Failed<ReviewRemarks>(Failure failure) ->
                Option.of(new ShownRemarks(
                        List.empty(),
                        Instant.EPOCH,
                        Option.of(failure),
                        true,
                        view.collapsed(),
                        view.sending(),
                        view.deleting()));
            case Loadable.Loading<ReviewRemarks>() -> Option.none();
        };
    }

    /**
     * The path of the node whose direct {@code files} they are: the one file, or the directory they are in, {@code /}
     * for the root.
     */
    private static String nodePath(List<CommitFile> files) {
        String first = files.head().path();
        return files.size() == 1 ? first : "/" + first.substring(0, first.lastIndexOf('/') + 1);
    }
}
