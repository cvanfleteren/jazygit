package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.ReadVersion;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Version;

/**
 * Showing the new version or the original of a file in place of its diff (ADR 102). The first time, the file is read
 * whole, as its diff was read but with whitespace not ignored; the diff shows until it is.
 */
public final class VersionUpdate {

    private VersionUpdate() {}

    public static Next update(Model model, VersionMsg msg) {
        return switch (msg) {
            case VersionMsg.Cycled(DiffCursorAt at) -> cycled(model, at);
        };
    }

    /**
     * Shows the next of the diff, the new version and the original of the one file of the area the cursor {@code at}
     * is in, those it has, reading the file whole first if it is not, or failed to be. Nothing happens in an area of
     * several files, or of a file that has no versions to show ({@link FileVersion#shown}).
     */
    private static Next cycled(Model model, DiffCursorAt at) {
        ScrolledContent content = at.at().content();
        return one(content).flatMap(file -> cycled(model, at, content, file)).getOrElse(() -> Next.of(model));
    }

    private static Option<Next> cycled(Model model, DiffCursorAt at, ScrolledContent content, ParsedFile file) {
        String path = file.file().path();
        Option<Version> before = Version.in(model, at.area(), content);
        boolean reads = before.forAll(version -> version.whole().isFailed());
        Option<ReadVersion> read = reads
                ? ExpandUpdate.source(model, content, path)
                        .map(source -> ReadVersion.of(
                                at.area(),
                                content,
                                source,
                                path,
                                model.preferences().diffOptions()))
                : before.map(Version::read);
        return read.map(how -> {
            Option<Loadable<DiffText>> reused = reads ? expanded(model, at.area(), content, path) : Option.none();
            Loadable<DiffText> whole = reused.orElse(
                            () -> before.filter(ignored -> !reads).map(Version::whole))
                    .getOrElse(Loadable::loading);
            Option<FileVersion> next = FileVersion.after(
                    before.flatMap(Version::shown), file.file().type());
            Version version = before.map(
                            kept -> kept.withShown(next).withRead(how).withWhole(whole))
                    .getOrElse(() -> new Version(content, path, next, how, whole));
            Scrolled was = at.at();
            Scrolled to = new Scrolled(
                    was.content(),
                    was.row(),
                    was.column(),
                    was.cursor().map(on -> version.moved(on, next)),
                    Option.none());
            Model shown = model.mapRepoView(
                            view -> view.withVersions(view.versions().put(at.area(), version)))
                    .withScrolled(model.scrolled().put(at.area(), to));
            return new Next(shown, reads && reused.isEmpty() ? List.<Cmd>of(how) : List.empty());
        });
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
    private static Option<Loadable<DiffText>> expanded(Model model, String area, ScrolledContent content, String path) {
        return model.repoView()
                .expanded()
                .get(area)
                .filter(expansion -> expansion.holdsFor(content))
                .flatMap(expansion -> expansion.files().get(path))
                .filter(file -> !file.read().options().ignoreWhitespace())
                .flatMap(file -> file.whole().toOption())
                .map(Loadable.Loaded::new);
    }
}
