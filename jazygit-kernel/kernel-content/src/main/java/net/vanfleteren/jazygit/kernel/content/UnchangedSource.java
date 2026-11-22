package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The node without changes highlighted among all files of the working tree (ADR 108): what a file holds, read from
 * disk, or how many files a directory holds. The file shown before stays while another is read.
 */
record UnchangedSource() implements ContentSource {

    /**
     * A file without changes highlighted among all files of the working tree, as it is on disk (ADR 108).
     */
    static final ContentArea AREA = ContentArea.of(AreaId.FILE_CONTENT, FilesPanel.PANE);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        if (!pane.equals(Mirrored.FILES)) {
            return Option.none();
        }
        return model.repoView()
                .files()
                .unchanged()
                .map(node -> FileNote.noted(
                        unchanged(model, node, Messages.get("content.panel.file.title"), Option.none())));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA, FileNote.AREA);
    }

    /**
     * The node without changes {@code node}, titled {@code title}: of the working tree, or of the commit {@code commit}
     * whose files are listed, as it is in it (ADR 128).
     */
    static Shown unchanged(Model model, UnchangedNode node, String title, Option<Sha> commit) {
        return switch (node) {
            case UnchangedNode.Directory(String path, int files) ->
                Shown.dim(title, Messages.get("content.directoryFiles", path.isEmpty() ? "/" : path, files));
            case UnchangedNode.File(var content) -> {
                Preview<String, FileContent> shown = content.shown();
                yield Shown.whenLoaded(
                        title, shown.value(), read -> fileContent(model, shown.key(), read, title, commit));
            }
        };
    }

    /**
     * What the file at {@code path} holds, {@code content}: text as a diff of itself, drawn in the rich view whatever
     * look diffs have, and anything else as a line that says what it is.
     */
    private static Shown fileContent(Model model, String path, FileContent content, String title, Option<Sha> commit) {
        return switch (content) {
            case FileContent.Text(DiffText asDiff) -> {
                DiffLook look = model.preferences().diffLook();
                yield new Shown.Area(
                        commit.isDefined() ? CommitFilesSource.AREA : AREA,
                        title,
                        new ScrolledContent.Unchanged(path, asDiff, commit),
                        new DiffBody(new ShownChanges(
                                Shows.shown(asDiff),
                                new DiffLook(DiffStyle.RICH, look.syntax(), look.words(), look.paired()))));
            }
            case FileContent.Binary(long size) -> Shown.dim(title, Messages.get("content.file.binarySize", size(size)));
            case FileContent.Link(String target) -> Shown.dim(title, Messages.get("content.file.link", target));
            case FileContent.Submodule(var at) ->
                Shown.dim(
                        title,
                        at.map(sha -> Messages.get("content.file.submodule", sha.shortSha()))
                                .getOrElse(() -> Messages.get("content.file.submoduleUnknown")));
        };
    }

    /**
     * {@code bytes} as a size to read: in bytes, KiB or MiB.
     */
    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes + 512) / 1024 + " KiB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }
}
