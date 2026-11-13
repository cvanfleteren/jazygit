package net.vanfleteren.jazygit.feature.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.settings.DiffStyle;
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
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.RepoView;
import net.vanfleteren.jazygit.state.view.UnchangedNode;

/**
 * Showing the new version or the original of a file in place of its diff (ADR 102), as source or rendered (ADR 103).
 * The first time, the file is read whole, as its diff was read but with whitespace not ignored; the diff shows until it
 * is. In the content panel, the keys act on the area the cursor is in; in the files pane, on every area that shows the
 * file highlighted, the unstaged area saying where the staged one goes too.
 */
public final class VersionUpdate {

    private VersionUpdate() {}

    public static Next update(Model model, VersionMsg msg) {
        return switch (msg) {
            case VersionMsg.Cycled(DiffCursorAt at) -> shown(model, List.of(Tuple.of(at.area(), at.at())), false);
            case VersionMsg.RenderToggled(DiffCursorAt at) -> shown(model, List.of(Tuple.of(at.area(), at.at())), true);
            case VersionMsg.FilesCycled _ -> shown(model, filesAreas(model), false);
            case VersionMsg.FilesRenderToggled _ -> shown(model, filesAreas(model), true);
        };
    }

    /**
     * Shows in each of the {@code areas} what follows what the first of them shows of its one file: the next of the
     * diff, the new version and the original, or, to {@code toggle} rendering, the same rendered or as source. An area
     * that does not have the version the first goes to shows its diff. Nothing happens in an area of several files, of
     * a file that has no versions to show ({@link FileVersion#shown}), or, to render it, of a file that cannot be
     * rendered ({@link Rendered}).
     */
    private static Next shown(Model model, List<Tuple2<AreaId, Scrolled>> areas, boolean toggle) {
        List<Tuple3<AreaId, Scrolled, ParsedFile>> showing =
                areas.flatMap(area -> one(area._2.content(), toggle).map(file -> Tuple.of(area._1, area._2, file)));
        return showing.headOption()
                .map(first -> next(model, first._1, first._2.content(), first._3, toggle))
                .map(next -> showing.foldLeft(
                        Next.of(model),
                        (done, area) -> done.then(now ->
                                showing(now, area._1, area._2, area._3, next).getOrElse(() -> Next.of(now)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * What the area {@code area}, showing {@code content}, shows next of its one {@code file}, to {@code toggle}
     * rendering or to cycle it.
     */
    private static Shows next(Model model, AreaId area, ScrolledContent content, ParsedFile file, boolean toggle) {
        Option<Version> before = model.repoView().version(area, content);
        boolean failed = before.exists(Version::failed);
        ChangeType type = file.file().type();
        return toggle ? toggled(before, failed, type) : cycled(before, failed, type);
    }

    /**
     * The area {@code area}, scrolled as {@code was} says, showing {@code target} of its one {@code file}, or its diff
     * where the file has not that version; the file is read whole first if it is not, or if reading it failed.
     */
    private static Option<Next> showing(Model model, AreaId area, Scrolled was, ParsedFile file, Shows target) {
        ScrolledContent content = was.content();
        String path = file.file().path();
        Option<Version> before = model.repoView().version(area, content);
        boolean reads = before.isEmpty() || before.exists(Version::failed);
        Option<ReadVersion> read = reads
                ? ExpandUpdate.source(model, content, path)
                        .map(source -> ReadVersion.of(
                                area, content, source, path, model.preferences().diffOptions()))
                : before.map(kept -> ReadVersion.of(area, kept.read()));
        Shows next = new Shows(
                target.version().filter(version -> version.in(file.file().type())), target.rendered());
        return read.map(how -> {
            // A file without changes is shown whole already, read from disk.
            Option<Loadable<DiffText>> reused = reads
                    ? content instanceof ScrolledContent.Unchanged(var onDisk, DiffText asDiff)
                            ? Option.of(new Loadable.Loaded<>(asDiff))
                            : expanded(model, area, content, path)
                    : Option.none();
            Loadable<DiffText> whole = reused.orElse(
                            () -> before.filter(ignored -> !reads).map(Version::whole))
                    .getOrElse(Loadable::loading);
            Version version = before.map(kept -> kept.withShown(next.version())
                            .withRendered(next.rendered())
                            .withRead(how.file())
                            .withWhole(whole))
                    .getOrElse(() -> new Version(content, path, next.version(), how.file(), whole, next.rendered()));
            Scrolled to = new Scrolled(
                    was.content(),
                    was.row(),
                    was.column(),
                    was.cursor().map(on -> version.moved(on, next.version())),
                    Option.none());
            Model shown = model.mapRepoView(
                            view -> view.withVersions(view.versions().put(area, version)))
                    .withScrolled(model.scrolled().put(area, to));
            return new Next(shown, reads && reused.isEmpty() ? List.<Cmd>of(how) : List.empty());
        });
    }

    /**
     * The areas the content panel shows the node highlighted in the files pane in, each where it is scrolled to, the
     * unstaged changes before the staged ones: what a commit or a stash whose files the pane lists changed in it, a
     * file without changes among all files (ADR 108), or the changes of the working tree. A diff counts only while the
     * rich view draws it, as a version shows only there (ADR 102).
     */
    private static List<Tuple2<AreaId, Scrolled>> filesAreas(Model model) {
        RepoView view = model.repoView();
        boolean rich = model.preferences().diffLook().style() == DiffStyle.RICH;
        List<Tuple2<AreaId, ScrolledContent>> areas = view.commitFiles()
                .map(files -> files.diff()
                        .map(Preview::shown)
                        .filter(diff -> rich && !diff.key().isEmpty())
                        .flatMap(diff -> diff.value()
                                .toOption()
                                .map(changes -> Tuple.<AreaId, ScrolledContent>of(
                                        AreaId.COMMIT_FILE,
                                        new ScrolledContent.FileChanges(files.of(), diff.key(), changes))))
                        .toList())
                .orElse(() -> view.unchanged().map(VersionUpdate::unchanged))
                .getOrElse(() -> view.fileDiff()
                        .map(Preview::shown)
                        .filter(ignored -> rich)
                        .flatMap(diff -> diff.value().toOption())
                        .map(diffs -> List.of(
                                        Tuple.<AreaId, ScrolledContent>of(
                                                AreaId.UNSTAGED, new ScrolledContent.Diff(diffs.unstaged())),
                                        Tuple.<AreaId, ScrolledContent>of(
                                                AreaId.STAGED, new ScrolledContent.Diff(diffs.staged())))
                                .filter(area -> area._2.richDiff().exists(diff -> !diff.isBlank())))
                        .getOrElse(List.empty()));
        return areas.map(area -> Tuple.of(area._1, model.scrolled(area._1, area._2)));
    }

    /**
     * The area a file without changes, {@code node}, shows in once it is read as text; none for a directory.
     */
    private static List<Tuple2<AreaId, ScrolledContent>> unchanged(UnchangedNode node) {
        return switch (node) {
            case UnchangedNode.File(var content) ->
                content.shown()
                        .value()
                        .toOption()
                        .filter(FileContent.Text.class::isInstance)
                        .map(FileContent.Text.class::cast)
                        .map(text -> Tuple.<AreaId, ScrolledContent>of(
                                AreaId.FILE_CONTENT,
                                new ScrolledContent.Unchanged(content.shown().key(), text.asDiff())))
                        .toList();
            case UnchangedNode.Directory _ -> List.empty();
        };
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
     * The one file of the diff {@code content} shows, if it has versions to show and, to {@code render} it, can be
     * rendered. A file without changes has one version only, shown already: it can be rendered, not cycled (ADR 108).
     */
    private static Option<ParsedFile> one(ScrolledContent content, boolean render) {
        return content.richDiff()
                .map(diff -> diff.parsed().files())
                .filter(files -> files.size() == 1)
                .map(files -> files.head())
                .filter(file -> FileVersion.shown(file.file()))
                .filter(file -> render || !(content instanceof ScrolledContent.Unchanged))
                .filter(file -> !render || Rendered.of(file.file().path()).isDefined());
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
