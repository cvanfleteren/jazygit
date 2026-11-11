package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.load.ReadVersion;

/**
 * Showing the new version or the original of a file in place of its diff (ADR 102), as source or rendered (ADR 103).
 * The first time, the file is read whole, as its diff was read but with whitespace not ignored; the diff shows until it
 * is.
 */
public final class VersionUpdate {

    private VersionUpdate() {}

    public static Next update(Model model, VersionMsg msg) {
        return switch (msg) {
            case VersionMsg.Cycled(DiffCursorAt at) -> shown(model, at, false);
            case VersionMsg.RenderToggled(DiffCursorAt at) -> shown(model, at, true);
        };
    }

    /**
     * Shows what follows what the area the cursor {@code at} is in shows of its one file, reading the file whole first
     * if it is not, or if reading it failed: the next of the diff, the new version and the original, or, to
     * {@code toggle} rendering, the same rendered or as source. Nothing happens in an area of several files, of a file
     * that has no versions to show ({@link FileVersion#shown}), or, to render it, of a file that cannot be rendered
     * ({@link Rendered}).
     */
    private static Next shown(Model model, DiffCursorAt at, boolean toggle) {
        ScrolledContent content = at.at().content();
        // A file without changes has one version only, shown already: it can be rendered, not cycled (ADR 108).
        return one(content)
                .filter(file -> toggle || !(content instanceof ScrolledContent.Unchanged))
                .filter(file -> !toggle || Rendered.of(file.file().path()).isDefined())
                .flatMap(file -> shown(model, at, content, file, toggle))
                .getOrElse(() -> Next.of(model));
    }

    private static Option<Next> shown(
            Model model, DiffCursorAt at, ScrolledContent content, ParsedFile file, boolean toggle) {
        String path = file.file().path();
        Option<Version> before = model.repoView().version(at.area(), content);
        boolean failed = before.exists(Version::failed);
        boolean reads = before.isEmpty() || failed;
        Option<ReadVersion> read = reads
                ? ExpandUpdate.source(model, content, path)
                        .map(source -> ReadVersion.of(
                                at.area(),
                                content,
                                source,
                                path,
                                model.preferences().diffOptions()))
                : before.map(kept -> ReadVersion.of(at.area(), kept.read()));
        return read.map(how -> {
            // A file without changes is shown whole already, read from disk.
            Option<Loadable<DiffText>> reused = reads
                    ? content instanceof ScrolledContent.Unchanged(var onDisk, DiffText asDiff)
                            ? Option.of(new Loadable.Loaded<>(asDiff))
                            : expanded(model, at.area(), content, path)
                    : Option.none();
            Loadable<DiffText> whole = reused.orElse(
                            () -> before.filter(ignored -> !reads).map(Version::whole))
                    .getOrElse(Loadable::loading);
            Shows next = toggle
                    ? toggled(before, failed, file.file().type())
                    : cycled(before, failed, file.file().type());
            Version version = before.map(kept -> kept.withShown(next.version())
                            .withRendered(next.rendered())
                            .withRead(how.file())
                            .withWhole(whole))
                    .getOrElse(() -> new Version(content, path, next.version(), how.file(), whole, next.rendered()));
            Scrolled was = at.at();
            Scrolled to = new Scrolled(
                    was.content(),
                    was.row(),
                    was.column(),
                    was.cursor().map(on -> version.moved(on, next.version())),
                    Option.none());
            Model shown = model.mapRepoView(
                            view -> view.withVersions(view.versions().put(at.area(), version)))
                    .withScrolled(model.scrolled().put(at.area(), to));
            return new Next(shown, reads && reused.isEmpty() ? List.<Cmd>of(how) : List.empty());
        });
    }

    /**
     * After {@code before}, the next of the diff, the new version and the original, those a file changed as
     * {@code type} has, rendered or not as before; when the version asked for {@code failed} to be read, that one again
     * rather than the next.
     */
    private static Shows cycled(Option<Version> before, boolean failed, ChangeType type) {
        Option<FileVersion> asked = before.flatMap(Version::shown);
        return new Shows(
                failed && asked.isDefined() ? asked : FileVersion.after(asked, type), before.exists(Version::rendered));
    }

    /**
     * After {@code before}, a version shown rendered as source, and anything else rendered: the version shown, else
     * the first a file changed as {@code type} has, the new one unless it was deleted (ADR 103). A version that
     * {@code failed} to be read is read again rendered.
     */
    private static Shows toggled(Option<Version> before, boolean failed, ChangeType type) {
        Option<FileVersion> asked = before.flatMap(Version::shown);
        boolean source = !failed && asked.isDefined() && before.exists(Version::rendered);
        return new Shows(asked.orElse(() -> FileVersion.after(Option.none(), type)), !source);
    }

    /**
     * The one file of the diff {@code content} shows, if it has versions to show.
     */
    private static Option<ParsedFile> one(ScrolledContent content) {
        return content.richDiff()
                .map(diff -> diff.parsed().files())
                .filter(files -> files.size() == 1)
                .map(files -> files.head())
                .filter(file -> FileVersion.shown(file.file()));
    }

    /**
     * The file with the path {@code path} read whole to expand it in the area {@code area}, if it was read with
     * whitespace not ignored, as a version is.
     */
    private static Option<Loadable<DiffText>> expanded(Model model, AreaId area, ScrolledContent content, String path) {
        return model.repoView()
                .expansion(area, content)
                .flatMap(expansion -> expansion.files().get(path))
                .filter(file -> !file.read().options().ignoreWhitespace())
                .flatMap(file -> file.whole().toOption())
                .map(Loadable.Loaded::new);
    }

    /**
     * What an area shows of its file next: the {@code version}, or the diff, and whether {@code rendered}.
     */
    private record Shows(Option<FileVersion> version, boolean rendered) {}
}
