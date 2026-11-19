package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.diff.WholeFile;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.i18n.Messages;

/**
 * What a source shows, with what the user did to the area it is shown in: the lines expanded in a diff (ADR 77), the
 * version of its file shown in place of it (ADR 102) and the diff filters (ADR 144). It holds for every source, so
 * none of them does it itself.
 */
final class Expanded {

    private Expanded() {}

    /**
     * {@code shown}, each of its areas as the model has it.
     */
    static Shown of(Model model, Shown shown) {
        return switch (shown) {
            case Shown.Area area -> of(model, area);
            case Shown.Stacked(List<Shown.Area> areas) -> new Shown.Stacked(areas.map(area -> of(model, area)));
            case Shown.Message message -> message;
        };
    }

    private static Shown.Area of(Model model, Shown.Area area) {
        Option<Expansion> expansion = model.repoView().expansion(area.area().id(), area.content());
        Option<Version> version = model.repoView()
                .version(area.area().id(), area.content())
                .orElse(() -> asVersion(model, area.content()));
        DiffContext context = model.preferences().context();
        Filtering filtering = model.filtering(area.area().id(), area.content());
        Body body = area.body()
                .withDiff(diff ->
                        diff.expanded(expansion, context).versioned(version).filtered(filtering));
        return new Shown.Area(area.area(), title(area, version), area.content(), body);
    }

    /**
     * The title of {@code area}, saying which {@code version} of its file it shows, if it shows one.
     */
    private static String title(Shown.Area area, Option<Version> version) {
        if (!(area.content() instanceof ScrolledContent.Unchanged)) {
            return version.map(shown -> versioned(area.title(), shown)).getOrElse(area.title());
        }
        // A file without changes has one version: the title says only whether it is rendered (ADR 108), or blamed
        // (ADR 147).
        if (version.exists(Version::rendered)) {
            return Messages.get("content.panel.file.rendered", area.title());
        }
        return version.filter(shown -> shown.blaming().isDefined())
                .map(shown -> blameNoted(Messages.get("content.panel.file.blamed", area.title()), shown))
                .getOrElse(area.title());
    }

    /**
     * The version a file without changes shows of itself, as source, when none was asked for: it is read whole, so
     * it shows with no line left out, and as Ctrl+T renders it (ADR 108).
     */
    private static Option<Version> asVersion(Model model, ScrolledContent content) {
        return content instanceof ScrolledContent.Unchanged unchanged
                ? Option.of(new Version(
                        content,
                        unchanged.path(),
                        Option.of(FileVersion.NEW),
                        new WholeFile(
                                content,
                                unchanged.source(),
                                unchanged.path(),
                                model.preferences().diffOptions()),
                        new Loadable.Loaded<>(unchanged.asDiff())))
                : Option.none();
    }

    /**
     * {@code title}, saying which version of its file the area shows in place of its diff, if it shows one, from the
     * moment it was asked for, and whether rendered (ADR 103): while the file is read, or when it could not be, it says
     * so too (ADR 102). Blamed, it says so, and while its blame is read, or when it could not be (ADR 147).
     */
    static String versioned(String title, Version version) {
        return version.shown()
                .map(shown -> Messages.get(versionKey(shown, version), title))
                .map(named -> readNoted(named, version))
                .getOrElse(title);
    }

    /**
     * The key of the message that says the area shows the version {@code shown} of its file, as {@code version} shows
     * it: rendered, blamed, or as it is.
     */
    private static String versionKey(FileVersion shown, Version version) {
        boolean isNew = shown == FileVersion.NEW;
        if (version.rendered()) {
            return isNew ? "content.panel.version.newRendered" : "content.panel.version.originalRendered";
        }
        if (version.blamed()) {
            return isNew ? "content.panel.version.newBlamed" : "content.panel.version.originalBlamed";
        }
        return isNew ? "content.panel.version.new" : "content.panel.version.original";
    }

    /**
     * {@code named}, saying that the file of {@code version} is read, or could not be, if it is; or else whether its
     * blame is.
     */
    private static String readNoted(String named, Version version) {
        // A read that has no such file shows the diff, as a failed one does, and says so as well.
        if (version.failed()) {
            return Messages.get("content.panel.version.failed", named);
        }
        if (version.whole() instanceof Loadable.Loading<DiffText>) {
            return Messages.get("content.panel.version.reading", named);
        }
        return blameNoted(named, version);
    }

    /**
     * {@code title}, saying that the blame of the version shown is read, or could not be, if it is (ADR 147).
     */
    private static String blameNoted(String title, Version version) {
        return version.blame()
                .map(blame -> switch (blame) {
                    case Loadable.Failed<?> _ -> Messages.get("content.panel.version.blameFailed", title);
                    case Loadable.Loading<?> _ -> Messages.get("content.panel.version.readingBlame", title);
                    case Loadable.Loaded<?> _ -> title;
                })
                .getOrElse(title);
    }
}
