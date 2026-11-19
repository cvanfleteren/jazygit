package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.temporal.ChronoUnit;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.ui.content.ContentSources;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.ShownChanges;
import net.vanfleteren.jazygit.ui.content.ShownDiff;
import net.vanfleteren.jazygit.ui.text.ShownTime;

/**
 * What the content panel shows in a model, apart from how it is drawn: what the first of the
 * {@linkplain ContentSources#standard() sources} that answers for the pane it mirrors gives (ADR 154), as the user
 * expanded, versioned and filtered it ({@link Expanded}).
 */
public final class ContentView {

    private ContentView() {}

    /**
     * What goes with the pane or area that has the focus, {@code focus}, as {@link #of(Model, Option)} says.
     */
    static Shown of(Model model, Focus focus) {
        return of(model, Option.of(focus));
    }

    /**
     * What goes with the pane that has the focus, {@code focus}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    static Shown of(Model model, Option<Focus> focus) {
        return of(model, focus, ContentSources.standard());
    }

    /**
     * What the first of {@code sources} that answers for the pane {@code focus} is or belongs to gives, as
     * {@link #of(Model, Option)} says; without one that answers, as without the app, it is loading.
     */
    static Shown of(Model model, Option<Focus> focus, ContentSources sources) {
        Mirrored mirrored = mirrored(model, focus, sources);
        return sources.sources()
                .iterator()
                .flatMap(source -> source.shown(model, mirrored))
                .headOption()
                .map(shown -> Expanded.of(model, shown))
                .getOrElse(() -> Shows.loading(""));
    }

    /**
     * What the content panel shows next to the pane that has the focus, {@code focus}, as far as a step of {@code J}
     * or {@code K} past a file the diff filters hide all of needs it (ADR 144): what each area shows, and how much of
     * the changes they show the filters hide: all of them, not all, or {@link Hidden#NO_DIFF} when no area or not
     * every area shows a diff.
     */
    public static Filtered filtered(Model model, Option<Focus> focus) {
        List<Shown.Area> areas = of(model, focus).areas();
        List<Option<ShownDiff>> diffs =
                areas.map(area -> area.body().changesShown().map(ShownChanges::diff));
        return new Filtered(areas.map(Shown.Area::content), hidden(diffs));
    }

    /**
     * How much of the changes {@code diffs} show the diff filters hide, {@code diffs} holding what each area shows if
     * it is a diff.
     */
    private static Hidden hidden(List<Option<ShownDiff>> diffs) {
        if (!allShowDiffs(diffs)) {
            return Hidden.NO_DIFF;
        }
        return diffs.flatMap(diff -> diff).forAll(ContentView::hidesAll) ? Hidden.ALL : Hidden.NOT_ALL;
    }

    /**
     * Whether there are areas, {@code diffs} holding what each shows if it is a diff, and each of them shows a diff.
     */
    private static boolean allShowDiffs(List<Option<ShownDiff>> diffs) {
        return diffs.nonEmpty() && diffs.forAll(Option::isDefined);
    }

    /**
     * Whether the diff filters hide all the changes of {@code diff}: it has files, and of each of them all its hunks.
     */
    private static boolean hidesAll(ShownDiff diff) {
        Expansion.Folded folded = diff.folded();
        return folded.diff().files().nonEmpty()
                && folded.diff()
                        .files()
                        .forAll(file -> folded.hiding(file.file().path()).all());
    }

    /**
     * The times the content shows (ADR 124): how long ago the comments of the review threads in a diff were written,
     * which it takes to the minute of the clock ({@code CommitFilesSource}).
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> !view.threads().isEmpty())
                .map(view -> ShownTime.reading(
                        now -> now.truncatedTo(ChronoUnit.MINUTES).toString()))
                .toList();
    }

    /**
     * What the content panel mirrors: on the pull requests screen, its lists; else the pane {@code focus} is or
     * belongs to; while a popup, or a pane the content does not mirror, has the focus, the pane, or the pane of the
     * area, that had it before. Nothing has the focus before the first render.
     */
    private static Mirrored mirrored(Model model, Option<Focus> focus, ContentSources sources) {
        if (model.screen() == Screen.PULL_REQUESTS) {
            return Mirrored.PULL_REQUESTS;
        }
        return focus.flatMap(on -> mirrored(model, on, sources))
                .orElse(() -> model.repoView().focusedPane().flatMap(kept -> mirrored(model, kept, sources)))
                .getOrElse(Mirrored.FILES);
    }

    /**
     * The pane {@code focus} is or belongs to, if the content mirrors it.
     */
    private static Option<Mirrored> mirrored(Model model, Focus focus, ContentSources sources) {
        return switch (focus) {
            case PaneId pane -> Mirrored.of(pane);
            case AreaId area -> sources.area(area).flatMap(shown -> Mirrored.of(shown.pane(model)));
            case Focus.Aside aside -> Option.none();
        };
    }

    /**
     * What the content panel shows, as {@link #filtered} gives it.
     *
     * @param contents what each of its areas shows
     * @param hidden   how much of the changes they show the diff filters hide: all, not all, or no diff is shown
     */
    public record Filtered(List<ScrolledContent> contents, Hidden hidden) {}

    /**
     * How much of the changes the content panel shows the diff filters hide (ADR 144).
     */
    public enum Hidden {
        /** All of them. */
        ALL,
        /** Not all of them. */
        NOT_ALL,
        /** No area shows a diff, as the log, or one of them does not, as while one is read. */
        NO_DIFF
    }
}
