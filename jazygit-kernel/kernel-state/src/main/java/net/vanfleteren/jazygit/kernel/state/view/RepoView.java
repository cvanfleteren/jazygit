package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import lombok.With;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.state.diff.Opened;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * What the repository screen shows, apart from what git reports: what is highlighted in its panes, the previews
 * read for that, and where its focus was. It stays while another screen is shown. Each part is owned by the
 * pane or the feature that fills it, so that a feature changes only its own part.
 *
 * @param branches    what the branches pane shows
 * @param commits     what the commits pane and the logs show
 * @param files       what the files pane shows
 * @param preview     what is read for the item highlighted in each pane
 * @param focusedPane the pane or area of the screen, outside the popups, that had the focus last, which a closing
 *                    popup gives it back to; none before the view reported one
 * @param areas       what each area of the content panel shows besides what it is given, by the id of the area; an
 *                    area that keeps nothing is not in it
 */
@With
public record RepoView(
        BranchesView branches,
        CommitsView commits,
        FilesView files,
        PreviewView preview,
        Option<Focus.OnScreen> focusedPane,
        Map<AreaId, AreaView> areas) {

    public static final Lens<RepoView, BranchesView> BRANCHES = Lens.of(RepoView::branches, RepoView::withBranches);

    public static final Lens<RepoView, CommitsView> COMMITS = Lens.of(RepoView::commits, RepoView::withCommits);

    public static final Lens<RepoView, FilesView> FILES = Lens.of(RepoView::files, RepoView::withFiles);

    public static final Lens<RepoView, PreviewView> PREVIEW = Lens.of(RepoView::preview, RepoView::withPreview);

    public static final RepoView INITIAL = new RepoView(
            BranchesView.INITIAL,
            CommitsView.INITIAL,
            FilesView.INITIAL,
            PreviewView.INITIAL,
            Option.none(),
            HashMap.empty());

    /**
     * This view with its branches part changed by {@code f}.
     */
    public RepoView mapBranches(UnaryOperator<BranchesView> f) {
        return withBranches(f.apply(branches));
    }

    /**
     * This view with its commits part changed by {@code f}.
     */
    public RepoView mapCommits(UnaryOperator<CommitsView> f) {
        return withCommits(f.apply(commits));
    }

    /**
     * This view with its files part changed by {@code f}.
     */
    public RepoView mapFiles(UnaryOperator<FilesView> f) {
        return withFiles(f.apply(files));
    }

    /**
     * This view with its previews changed by {@code f}.
     */
    public RepoView mapPreview(UnaryOperator<PreviewView> f) {
        return withPreview(f.apply(preview));
    }

    /**
     * The version each area keeps, by the id of the area.
     */
    public Map<AreaId, Version> versions() {
        return areas.flatMap((area, view) -> view.version().map(version -> Tuple.of(area, version)));
    }

    /**
     * The lines expanded in each area that has some, by the id of the area.
     */
    public Map<AreaId, Expansion> expansions() {
        return areas.flatMap((area, view) -> view.expanded().map(expansion -> Tuple.of(area, expansion)));
    }

    /**
     * The version kept for the area with the id {@code area}, if there is one.
     */
    public Option<Version> versionOf(AreaId area) {
        return areas.get(area).flatMap(AreaView::version);
    }

    /**
     * The lines expanded in the area with the id {@code area}, if there are some.
     */
    public Option<Expansion> expandedOf(AreaId area) {
        return areas.get(area).flatMap(AreaView::expanded);
    }

    /**
     * The version kept for the area with the id {@code area} while it shows {@code content}, if it holds for that.
     */
    public Option<Version> version(AreaId area, ScrolledContent content) {
        return versionOf(area).filter(version -> version.holdsFor(content));
    }

    /**
     * The lines expanded in the area with the id {@code area} while it shows {@code content}, if they hold for that.
     */
    public Option<Expansion> expansion(AreaId area, ScrolledContent content) {
        return expandedOf(area).filter(kept -> kept.holdsFor(content));
    }

    /**
     * The hunks filters hide that were opened in the area with the id {@code area} while it shows {@code content}, if
     * they hold for that (ADR 144).
     */
    public Option<Opened> opened(AreaId area, ScrolledContent content) {
        return areas.get(area).flatMap(AreaView::opened).filter(kept -> kept.holdsFor(content));
    }

    /**
     * This view with {@code opened} kept for the area with the id {@code area} (ADR 144).
     */
    public RepoView withOpened(AreaId area, Opened opened) {
        return mapArea(area, view -> view.withOpened(Option.of(opened)));
    }

    /**
     * This view with {@code version} kept for the area with the id {@code area}.
     */
    public RepoView withVersion(AreaId area, Version version) {
        return mapArea(area, view -> view.withVersion(Option.of(version)));
    }

    /**
     * This view with {@code expansion} kept for the area with the id {@code area}.
     */
    public RepoView withExpansion(AreaId area, Expansion expansion) {
        return mapArea(area, view -> view.withExpanded(Option.of(expansion)));
    }

    /**
     * This view with no version kept for any area.
     */
    public RepoView withoutVersions() {
        return withAreas(
                areas.mapValues(view -> view.withVersion(Option.none())).filterValues(view -> !view.isEmpty()));
    }

    /**
     * This view with no lines expanded in any area.
     */
    public RepoView withoutExpansions() {
        return withAreas(
                areas.mapValues(view -> view.withExpanded(Option.none())).filterValues(view -> !view.isEmpty()));
    }

    private RepoView mapArea(AreaId area, UnaryOperator<AreaView> f) {
        AreaView changed = f.apply(areas.get(area).getOrElse(AreaView.INITIAL));
        return withAreas(changed.isEmpty() ? areas.remove(area) : areas.put(area, changed));
    }
}
