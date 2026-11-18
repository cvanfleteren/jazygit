package net.vanfleteren.jazygit.content;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.content.diff.ShownRemarks;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.diff.WholeFile;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import net.vanfleteren.jazygit.state.view.BuildsShown;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.NoLog;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.state.view.WorkflowRuns;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.AheadView;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.stash.StashPanel;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.text.ShownTime;

/**
 * What the content panel shows in a model, apart from how it is drawn: it mirrors whichever left pane is in use,
 * showing the file diff, the branch log, the commit diff/body or what a stash stashed; on the pull requests screen,
 * the highlighted pull request.
 */
public final class ContentView {

    private static final DiffText NO_CHANGES = DiffText.of("");

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
        if (model.screen() == Screen.PULL_REQUESTS) {
            return pullRequest(model);
        }
        return expanded(model, mirrored(model, focus));
    }

    /**
     * What the content panel shows next to the pane that has the focus, {@code focus}, as far as a step of {@code J}
     * or {@code K} past a file the diff filters hide all of needs it (ADR 144): what each area shows, and whether all
     * the changes they show are hidden; none when an area shows no diff, as while one is read.
     */
    public static Filtered filtered(Model model, Option<Focus> focus) {
        List<Shown.Area> areas = of(model, focus).areas();
        List<Option<ShownDiff>> diffs =
                areas.map(area -> area.body().changesShown().map(ShownChanges::diff));
        return new Filtered(
                areas.map(Shown.Area::content),
                Option.when(
                        diffs.nonEmpty() && diffs.forAll(Option::isDefined),
                        () -> diffs.flatMap(diff -> diff).forAll(ContentView::allHidden)));
    }

    /**
     * Whether the diff filters hide all the changes {@code diff} shows (ADR 144).
     */
    private static boolean allHidden(ShownDiff diff) {
        Expansion.Folded folded = diff.folded();
        return folded.diff().files().nonEmpty()
                && folded.diff()
                        .files()
                        .forAll(file -> folded.hiding(file.file().path()).all());
    }

    /**
     * The times the content shows (ADR 124): how long ago the comments of the review threads in a diff were written,
     * which it takes to the minute of the clock ({@link #remarks}).
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
     * What the repository screen shows next to the pane {@code mirrored} has highlighted.
     */
    private static Shown repository(Model model, Mirrored mirrored) {
        return switch (mirrored) {
            case FILES ->
                model.repoView()
                        .files()
                        .commitFiles()
                        .map(view -> commitFiles(model, view))
                        .getOrElse(() -> model.repoView()
                                .files()
                                .unchanged()
                                .map(node -> unchanged(model, node))
                                .getOrElse(() -> fileDiffs(model)));
            case BRANCHES -> branches(model);
            case COMMITS ->
                commit(
                        model,
                        ContentArea.COMMIT,
                        model.repoView().preview().commitDetail(),
                        model::commit,
                        Option.when(model.repo().commits().toOption().exists(List::isEmpty), "content.noCommits"));
            case STASH -> stash(model);
        };
    }

    /**
     * What the repository screen shows next to the pane {@code mirrored} has highlighted, each diff with the lines
     * expanded in the area it is shown in (ADR 77). What the files pane says in place of a diff is a note, an area
     * that holds the focus while the files are walked from the content panel (ADR 117).
     */
    private static Shown expanded(Model model, Mirrored mirrored) {
        return switch (repository(model, mirrored)) {
            case Shown.Message(String title, String text, Tone tone)
            when mirrored == Mirrored.FILES ->
                new Shown.Area(ContentArea.FILE_NOTE, title, new ScrolledContent.Note(text), new Body.Note(text, tone));
            case Shown.Area area -> expanded(model, area);
            case Shown.Stacked(List<Shown.Area> areas) -> new Shown.Stacked(areas.map(area -> expanded(model, area)));
            case Shown other -> other;
        };
    }

    private static Shown.Area expanded(Model model, Shown.Area area) {
        Option<Expansion> expansion = model.repoView().expansion(area.area().id(), area.content());
        Option<Version> version = model.repoView()
                .version(area.area().id(), area.content())
                .orElse(() -> asVersion(model, area.content()));
        DiffContext context = model.preferences().context();
        Filtering filtering = model.filtering(area.area().id(), area.content());
        Body body = area.body()
                .withDiff(diff ->
                        diff.expanded(expansion, context).versioned(version).filtered(filtering));
        return new Shown.Area(area.area(), expandedTitle(area, version), area.content(), body);
    }

    /**
     * The title of {@code area}, saying which {@code version} of its file it shows, if it shows one.
     */
    private static String expandedTitle(Shown.Area area, Option<Version> version) {
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
     * The node without changes highlighted among all files of the working tree (ADR 108): what a file holds, read
     * from disk, or how many files a directory holds. The file shown before stays while another is read.
     */
    private static Shown unchanged(Model model, UnchangedNode node) {
        return unchanged(model, node, Messages.get("content.panel.file.title"), Option.none());
    }

    /**
     * The node without changes {@code node}, titled {@code title}: of the working tree, or of the commit {@code commit}
     * whose files are listed, as it is in it (ADR 128).
     */
    private static Shown unchanged(Model model, UnchangedNode node, String title, Option<Sha> commit) {
        return switch (node) {
            case UnchangedNode.Directory(String path, int files) ->
                dim(title, Messages.get("content.directoryFiles", path.isEmpty() ? "/" : path, files));
            case UnchangedNode.File(var content) -> {
                Preview<String, FileContent> shown = content.shown();
                yield whenLoaded(title, shown.value(), read -> fileContent(model, shown.key(), read, title, commit));
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
                        commit.isDefined() ? ContentArea.COMMIT_FILE : ContentArea.FILE_CONTENT,
                        title,
                        new ScrolledContent.Unchanged(path, asDiff, commit),
                        new Body.Diff(new ShownChanges(
                                shown(asDiff), new DiffLook(DiffStyle.RICH, look.syntax(), look.words()))));
            }
            case FileContent.Binary(long size) -> dim(title, Messages.get("content.file.binarySize", size(size)));
            case FileContent.Link(String target) -> dim(title, Messages.get("content.file.link", target));
            case FileContent.Submodule(var at) ->
                dim(
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

    /**
     * {@code diff}, with a line saying it is truncated when it was cut off.
     */
    static ShownDiff shown(DiffText diff) {
        return new ShownDiff(diff, Option.when(diff.truncated(), () -> Messages.get("content.git.diff.truncated")));
    }

    /**
     * What a commit changed as it is shown: when changes to whitespace only are left out and nothing else
     * changed, a line that says so.
     */
    static ShownDiff shownChanges(DiffText changes, DiffOptions options) {
        return changes.isBlank() && options.ignoreWhitespace()
                ? new ShownDiff(NO_CHANGES, Option.of(Messages.get("content.onlyWhitespace")))
                : shown(changes);
    }

    /**
     * {@code title}, saying how much context is shown when it is not git's own (ADR 76), and that changes to
     * whitespace only are left out when they are.
     */
    static String titled(String title, DiffOptions options) {
        String withContext = withContext(title, options.context());
        return options.ignoreWhitespace() ? Messages.get("content.panel.ignoringWhitespace", withContext) : withContext;
    }

    /**
     * {@code title}, saying how much context is shown when it is not git's own (ADR 76).
     */
    private static String withContext(String title, DiffContext context) {
        if (context.equals(DiffContext.DEFAULT)) {
            return title;
        }
        return context.wholeFile()
                ? Messages.get("content.panel.wholeFile", title)
                : Messages.get("content.panel.context", title, context.lines());
    }

    /**
     * The pane {@code focus} is or belongs to; while a popup, or a pane the content does not mirror, has the focus,
     * the pane, or the pane of the area, that had it before. Nothing has the focus before the first render.
     */
    private static Mirrored mirrored(Model model, Option<Focus> focus) {
        return focus.flatMap(on -> mirrored(model, on))
                .orElse(() -> model.repoView().focusedPane().flatMap(kept -> mirrored(model, kept)))
                .getOrElse(Mirrored.FILES);
    }

    /**
     * The pane {@code focus} is or belongs to, if the content mirrors it.
     */
    private static Option<Mirrored> mirrored(Model model, Focus focus) {
        return switch (focus) {
            case PaneId pane -> Mirrored.of(pane);
            case AreaId area -> ContentArea.of(area).flatMap(shown -> Mirrored.of(shown.pane(model)));
            case Focus.Aside aside -> Option.none();
        };
    }

    private static Shown loading(String title) {
        return dim(title, Placeholders.loading());
    }

    private static Shown dim(String title, String text) {
        return new Shown.Message(title, text, Tone.DIM);
    }

    private static <T> Shown whenLoaded(String title, Loadable<T> loadable, Function<T, Shown> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> loading(title);
            case Loadable.Failed<T>(Failure failure) ->
                new Shown.Message(title, Placeholders.error(failure), Tone.ERROR);
        };
    }

    /**
     * The pull request highlighted in the pane of the pull requests screen in use; the one shown before stays while
     * it is read.
     */
    private static Shown pullRequest(Model model) {
        String title = Messages.get("content.panel.pullRequest.title");
        return model.prOverview()
                .preview()
                .map(Preview::shown)
                .map(preview -> pullRequest(title, preview))
                .getOrElse(() -> dim(title, Messages.get("content.noPullRequest")));
    }

    private static Shown pullRequest(String title, Preview<PullRequestSummary, PullRequestPreview> preview) {
        PullRequestSummary pullRequest = preview.key();
        return whenLoaded(
                title,
                preview.value(),
                read -> new Shown.Area(
                        ContentArea.PULL_REQUEST,
                        Messages.get(
                                "content.panel.pullRequest.titleFor",
                                pullRequest.repository(),
                                String.valueOf(pullRequest.number().value())),
                        new ScrolledContent.PullRequest(pullRequest, read),
                        new Body.PullRequest(pullRequest, read)));
    }

    /**
     * One area for the staged changes and one for the unstaged changes, one above the other when both exist.
     */
    private static Shown fileDiffs(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        String title = titled(Messages.get("panel.diff.title"), options);
        Option<RepoStatus> status = model.repo().status().toOption();
        // With no files there is nothing to highlight, so no diff is ever requested.
        if (status.exists(read -> read.files().isEmpty())) {
            return dim(title, Messages.get("content.noChangedFiles"));
        }
        if (status.exists(read -> FilesPanel.noneMatch(model, read.files()))) {
            return dim(title, Messages.get("panel.files.noMatches"));
        }
        return model.repoView()
                .preview()
                .fileDiff()
                .map(Preview::shown)
                .map(diff -> whenLoaded(
                        title,
                        diff.value(),
                        diffs -> diffAreas(options, model.preferences().diffLook(), title, diffs)))
                .getOrElse(() -> loading(title));
    }

    /**
     * The staged and the unstaged changes of {@code diffs}, each drawn as {@code look} says on its own (ADR 47).
     */
    private static Shown diffAreas(DiffOptions options, DiffLook look, String title, Diffs diffs) {
        if (diffs.isEmpty()) {
            return dim(
                    title, Messages.get(options.ignoreWhitespace() ? "content.onlyWhitespace" : "content.noChanges"));
        }
        return new Shown.Stacked(List.of(
                        Tuple.of(ContentArea.STAGED, "content.staged", diffs.staged()),
                        Tuple.of(ContentArea.UNSTAGED, "content.unstaged", diffs.unstaged()))
                .filter(area -> !shown(area._3).isBlank())
                .map(area -> new Shown.Area(
                        area._1,
                        titled(Messages.get(area._2), options),
                        new ScrolledContent.Diff(area._3),
                        new Body.Diff(new ShownChanges(shown(area._3), look)))));
    }

    /**
     * The commit highlighted in the divergence the local branches pane lists, or in the commits of a branch the pane
     * lists, or else the log of the branch.
     */
    private static Shown branches(Model model) {
        return model.repoView()
                .branches()
                .divergence()
                .filter(view -> model.repoView().branches().tab() == BranchesTab.LOCAL)
                .map(view -> commit(
                        model,
                        ContentArea.DIVERGENCE,
                        view.detail(),
                        view::commit,
                        Option.when(view.commits().toOption().exists(Divergence::isEmpty), "content.noDivergence")))
                .orElse(() -> model.repoView()
                        .branches()
                        .branchCommits()
                        .map(view -> commit(
                                model,
                                ContentArea.BRANCH_COMMIT,
                                view.detail(),
                                sha -> model.branchCommit(view, sha),
                                Option.when(
                                        view.commits(model).toOption().exists(List::isEmpty), "content.noCommits"))))
                .orElse(() -> builds(model))
                .getOrElse(() -> branchLog(model));
    }

    /**
     * The builds of the local branch highlighted in the branches pane, while they are shown for it (ADR 131).
     */
    private static Option<Shown> builds(Model model) {
        return model.repoView()
                .branches()
                .builds()
                .filter(view -> model.repoView().branches().tab() == BranchesTab.LOCAL
                        && model.repoView()
                                .preview()
                                .branchLog()
                                .exists(log -> log.key().equals(new BranchRef.Local(view.branch()))))
                .map(ContentView::builds);
    }

    /**
     * The builds {@code view} shows of its branch: the chart and list of them once some are read, or else why there
     * are none.
     */
    private static Shown builds(BuildsView view) {
        String title = buildsTitle(view);
        return whenLoaded(
                title,
                view.builds(),
                // Builds show only for a branch that follows one on GitHub.
                builds -> view.shownOf()
                        .filter(drawn -> view.onGitHub().isDefined())
                        .<Shown>map(drawn -> new Shown.Area(
                                ContentArea.BUILDS,
                                title,
                                new ScrolledContent.Builds(view.branch()),
                                new Body.Builds(drawn)))
                        .getOrElse(() -> noBuilds(view, title, NoBuilds.of(view, builds))));
    }

    /**
     * The title of the builds {@code view} shows: of which branch, whether more are read, and whether more can be.
     */
    private static String buildsTitle(BuildsView view) {
        String branch = view.branch().value();
        String commits = String.valueOf(view.commits());
        String ofBranch;
        if (view.reading()) {
            ofBranch = Messages.get("content.panel.builds.reading", branch, commits);
        } else if (view.stopped().isDefined()) {
            ofBranch = Messages.get("content.panel.builds.stopped", branch, commits);
        } else {
            ofBranch = Messages.get("content.panel.builds.title", branch, commits);
        }
        Option<WorkflowRuns> runs = view.shownTab().flatMap(tab -> view.runsOf(tab.workflow()));
        // On every branch, the title says when more runs of the tab are being read, as m asked: the first ones say so
        // in the body.
        String every = view.allBranches()
                ? runs.filter(ofTab -> ofTab.reading() && ofTab.count() > 0)
                        .map(ofTab -> Messages.get(
                                "content.panel.builds.everyBranchReading", ofBranch, String.valueOf(ofTab.count())))
                        .getOrElse(() -> Messages.get("content.panel.builds.everyBranch", ofBranch))
                : ofBranch;
        // Older builds can be read with m, or read again after failing: of the branch, or on every branch, of the tab
        // (ADR 132).
        boolean more = view.allBranches() ? runs.exists(WorkflowRuns::hasMore) : view.hasMore();
        if (!more) {
            return every;
        }
        boolean failed = view.allBranches() ? runs.exists(WorkflowRuns::hasFailed) : view.hasFailed();
        return Messages.get(failed ? "content.panel.builds.retry" : "content.panel.builds.more", every);
    }

    /**
     * What the content panel says in place of the builds of {@code view}, titled {@code title}, as {@code state} has
     * it.
     */
    private static Shown noBuilds(BuildsView view, String title, NoBuilds state) {
        String remote = view.onGitHub().map(RemoteBranch::shortName).getOrElse("");
        return switch (state) {
            case OFF_GITHUB ->
                dim(
                        title,
                        Messages.get("content.builds.offGitHub", view.branch().value()));
            case READING -> loading(title);
            case NOT_ON_GITHUB -> dim(title, Messages.get("content.builds.notOnGitHub", remote));
            case NONE -> dim(title, Messages.get("content.builds.none", remote, String.valueOf(view.commits())));
            case NONE_AHEAD -> dim(title, Messages.get("content.builds.noneAhead"));
        };
    }

    /**
     * Why the content panel shows no builds of a branch (ADR 131).
     */
    private enum NoBuilds {
        /** The branch follows no branch on GitHub. */
        OFF_GITHUB,
        /** None is read yet, while the first commits are. */
        READING,
        /** The branch it follows is not on GitHub. */
        NOT_ON_GITHUB,
        /** None of the commits read has builds. */
        NONE,
        /** Some were read, but none on the commits not on the base. */
        NONE_AHEAD;

        /**
         * Why {@code view}, which read {@code builds}, shows none.
         */
        static NoBuilds of(BuildsView view, List<Build> builds) {
            if (view.onGitHub().isEmpty()) {
                return OFF_GITHUB;
            }
            if (builds.nonEmpty()) {
                return NONE_AHEAD;
            }
            if (view.reading()) {
                return READING;
            }
            return view.commits() == 0 ? NOT_ON_GITHUB : NONE;
        }
    }

    private static Shown branchLog(Model model) {
        return model.repoView()
                .preview()
                .branchLog()
                .map(Preview::shown)
                .map(log -> branchLog(model, log))
                .getOrElse(() -> model.repoView()
                        .branches()
                        .noLog()
                        .map(noLog -> dim(Messages.get("content.panel.log.title"), noLogText(noLog)))
                        .getOrElse(() -> loading(Messages.get("content.panel.log.title"))));
    }

    /**
     * The log of the branch {@code log} is of, titled with how far the branch is ahead; the one shown before stays
     * while it is read.
     */
    private static Shown branchLog(Model model, Preview<BranchRef, List<Commit>> log) {
        String title = Messages.get("content.panel.log.titleFor", log.key().name());
        AheadView ahead = new AheadView(AheadUpdate.branch(model, log.key()), AheadUpdate.distance(model, log.key()));
        return whenLoaded(
                ahead.title(title),
                log.value(),
                commits -> new Shown.Area(
                        ContentArea.LOG,
                        ahead.title(title),
                        new ScrolledContent.BranchLog(commits),
                        new Body.BranchLog(commits, ahead)));
    }

    /**
     * Why there is no log to show, {@code noLog}, as it is said.
     */
    private static String noLogText(NoLog noLog) {
        return switch (noLog) {
            case NoLog.Unfetched(var pullRequest) ->
                Messages.get("content.unfetched", String.valueOf(pullRequest.value()));
            case NoLog.Detached(var at) -> Messages.get("content.detached", at.shortSha());
        };
    }

    /**
     * The commit {@code detail} is of, found by {@code commit}, in {@code area} once what it changed is read, as the
     * commits pane shows its commits; the one shown before stays while it is read. Before one is highlighted, the
     * message {@code nothing} says there is nothing to highlight, if it does.
     */
    private static Shown commit(
            Model model,
            ContentArea area,
            Option<Preview<Sha, DiffText>> detail,
            Function<Sha, Option<Commit>> commit,
            Option<String> nothing) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("content.panel.commit.title");
        DiffLook look = model.preferences().diffLook();
        return detail.map(Preview::shown)
                .map(shown -> whenLoaded(
                        title,
                        shown.value(),
                        changes -> commit.apply(shown.key())
                                .<Shown>map(found -> commitArea(area, found, changes, options, look))
                                .getOrElse(() -> loading(title))))
                .getOrElse(() -> nothing.map(message -> dim(title, Messages.get(message)))
                        .getOrElse(() -> loading(title)));
    }

    private static Shown.Area commitArea(
            ContentArea area, Commit commit, DiffText changes, DiffOptions options, DiffLook look) {
        return new Shown.Area(
                area,
                titled(Messages.get("content.panel.commit.titleFor", commit.shortSha()), options),
                new ScrolledContent.CommitChanges(commit, changes),
                new Body.CommitChanges(commit, new ShownChanges(shownChanges(changes, options), look)));
    }

    /**
     * What the commit or the stash whose files the files pane lists changed in the files of the node highlighted in
     * them; what was shown before stays while it is read. Before a node is highlighted, it is loading, or says that
     * nothing changed.
     */
    private static Shown commitFiles(Model model, CommitFilesView view) {
        DiffOptions options = model.preferences().diffOptions();
        String name = FilesPanel.changesName(model, view.of());
        String title = titled(Messages.get("panel.commitFiles.titleFor", name), options);
        // A node the commit did not change, among all its files, shows as it is in it (ADR 128).
        Option<Shown> unchanged = view.unchanged()
                .filter(node -> view.allFiles())
                .map(node -> unchanged(
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
                .map(nothing -> whenLoaded(title, view.listedFiles(), listed -> dim(title, nothing)))
                // Nor when the filter hides all files listed (ADR 105).
                .orElse(() -> view.listedFiles()
                        .toOption()
                        .filter(listed -> FilesPanel.noneMatch(model, listed))
                        .map(listed -> dim(title, Messages.get("panel.files.noMatches"))));
        return nothingListed.getOrElse(() -> view.diff()
                .map(Preview::shown)
                .map(diff -> whenLoaded(
                        title,
                        diff.value(),
                        changes -> diff.key().isEmpty()
                                ? dim(title, Messages.get("content.noDirectFiles"))
                                : fileChanges(model, view, name, diff.key(), changes)))
                .getOrElse(() -> whenLoaded(
                        title,
                        view.files(),
                        files -> files.isEmpty() ? dim(title, Messages.get("content.noChanges")) : loading(title))));
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
                ContentArea.COMMIT_FILE,
                titled(Messages.get("content.panel.commitFile.titleFor", name, nodePath(files)), options),
                new ScrolledContent.FileChanges(view.of(), files, changes),
                new Body.Diff(
                        new ShownChanges(
                                shownChanges(changes, options),
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

    /**
     * What the highlighted stash stashed; the stash shown before stays while it is read. Without stashes, it says
     * there are none.
     */
    private static Shown stash(Model model) {
        String title = Messages.get("panel.stash.title");
        return model.repoView()
                .preview()
                .stashDetail()
                .map(Preview::shown)
                .flatMap(detail -> model.repo()
                        .stash(detail.key())
                        .map(stash -> whenLoaded(title, detail.value(), changes -> stashArea(model, stash, changes))))
                .getOrElse(() -> whenLoaded(
                        title,
                        model.repo().stashes(),
                        stashes -> stashes.isEmpty() ? dim(title, Messages.get("content.noStashes")) : loading(title)));
    }

    private static Shown stashArea(Model model, Stash stash, DiffText changes) {
        DiffOptions options = model.preferences().diffOptions();
        return new Shown.Area(
                ContentArea.STASH,
                titled(Messages.get("content.panel.stash.titleFor", stash.ref()), options),
                new ScrolledContent.StashChanges(stash.sha(), changes),
                new Body.StashChanges(
                        stash,
                        new ShownChanges(
                                shownChanges(changes, options),
                                model.preferences().diffLook())));
    }

    /**
     * How a message is drawn.
     */
    enum Tone {
        DIM,
        ERROR
    }

    /**
     * What the content panel shows: a message, or areas with something to scroll through.
     */
    public sealed interface Shown {

        /**
         * The areas shown, top to bottom; none for a message.
         */
        default List<Area> areas() {
            return switch (this) {
                case Message _ -> List.empty();
                case Area area -> List.of(area);
                case Stacked(List<Area> areas) -> areas;
            };
        }

        /**
         * A panel titled {@code title} with only {@code text}: what is still loading, failed or is not there.
         */
        record Message(String title, String text, Tone tone) implements Shown {}

        /**
         * The area {@code area}, titled {@code title}, showing {@code body}; where it is scrolled to holds for
         * {@code content}.
         */
        record Area(ContentArea area, String title, ScrolledContent content, Body body) implements Shown {}

        /**
         * The areas one above the other.
         */
        record Stacked(List<Area> areas) implements Shown {}
    }

    /**
     * What an area shows, as it is styled.
     */
    public sealed interface Body {

        /**
         * The diff this shows and how it is drawn, if it shows one.
         */
        default Option<ShownChanges> changesShown() {
            return switch (this) {
                case Diff(ShownChanges changes, var _) -> Option.of(changes);
                case CommitChanges(var _, ShownChanges changes) -> Option.of(changes);
                case StashChanges(var _, ShownChanges changes) -> Option.of(changes);
                case BranchLog _, Builds _, PullRequest _, Note _ -> Option.none();
            };
        }

        /**
         * This, with the diff it shows, if it shows one, changed by {@code change}.
         */
        default Body withDiff(UnaryOperator<ShownDiff> change) {
            return switch (this) {
                case Diff(ShownChanges changes, var remarks) -> new Diff(changes.withDiff(change), remarks);
                case CommitChanges(Commit commit, ShownChanges changes) ->
                    new CommitChanges(commit, changes.withDiff(change));
                case StashChanges(Stash stash, ShownChanges changes) ->
                    new StashChanges(stash, changes.withDiff(change));
                case BranchLog _, Builds _, PullRequest _, Note _ -> this;
            };
        }

        /**
         * A unified diff: the staged or the unstaged changes of the working tree, or what a commit, a stash or a pull
         * request changed in some of its files, with the review threads of a pull request (ADR 55).
         */
        record Diff(ShownChanges changes, Option<ShownRemarks> remarks) implements Body {

            /**
             * A diff without review threads.
             */
            public Diff(ShownChanges changes) {
                this(changes, Option.none());
            }
        }

        /**
         * The log of a branch, as far as {@code ahead} shows it.
         */
        record BranchLog(List<Commit> commits, AheadView ahead) implements Body {}

        /**
         * The builds of a branch, of the newest commit first (ADR 131).
         */
        record Builds(BuildsShown shown) implements Body {}

        /**
         * A commit and what it changed.
         */
        record CommitChanges(Commit commit, ShownChanges changes) implements Body {}

        /**
         * A stash and what it stashed.
         */
        record StashChanges(Stash stash, ShownChanges changes) implements Body {}

        /**
         * A pull request, and what it says and changes.
         */
        record PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) implements Body {}

        /**
         * What the files pane says in place of a diff or a file's content, drawn as {@code tone} says (ADR 117).
         */
        record Note(String text, Tone tone) implements Body {}
    }

    /**
     * A diff an area shows, {@code diff}, drawn as {@code look} says.
     */
    public record ShownChanges(ShownDiff diff, DiffLook look) {

        /**
         * These changes with their diff changed by {@code change}.
         */
        ShownChanges withDiff(UnaryOperator<ShownDiff> change) {
            return new ShownChanges(change.apply(diff), look);
        }
    }

    /**
     * A diff as it is shown: {@code diff}, then the line {@code note}, which git did not print, if there is one, with
     * the lines {@code expansion} expanded around its hunks, folded to {@code context} lines (ADR 77), and its file
     * shown as the {@code version} of it the area shows, if it shows one (ADR 102).
     */
    public record ShownDiff(
            DiffText diff,
            Option<String> note,
            Option<Expansion> expansion,
            DiffContext context,
            Option<Version> version,
            Filtering filtering) {

        /**
         * {@code diff} and its note, as git gives it.
         */
        public ShownDiff(DiffText diff, Option<String> note) {
            this(diff, note, Option.none(), DiffContext.DEFAULT, Option.none(), Filtering.NONE);
        }

        /**
         * This diff with the lines of {@code expanded} shown, folded to {@code folding} lines of context (ADR 77).
         */
        ShownDiff expanded(Option<Expansion> expanded, DiffContext folding) {
            return new ShownDiff(diff, note, expanded, folding, version, filtering);
        }

        /**
         * This diff with its file shown as {@code shown} has it, if it shows a version of it (ADR 102).
         */
        ShownDiff versioned(Option<Version> shown) {
            return new ShownDiff(diff, note, expansion, context, shown, filtering);
        }

        /**
         * This diff with the hunks {@code hiding} hides cut out (ADR 144).
         */
        ShownDiff filtered(Filtering hiding) {
            return new ShownDiff(diff, note, expansion, context, version, hiding);
        }

        /**
         * The diff shown parsed, its files read whole folded, with what is known of each of them (ADR 77), its file
         * replaced by the version of it shown, if one is (ADR 102).
         */
        public Expansion.Folded folded() {
            Expansion.Folded expanded = expansion
                    .map(kept -> kept.folded(diff.parsed(), context))
                    .getOrElse(() -> Expansion.Folded.of(diff.parsed()));
            Expansion.Folded folded =
                    filtering.hide(version.map(shown -> shown.applied(expanded)).getOrElse(expanded), diff.parsed());
            return note.map(line -> folded.withDiff(folded.diff().withNote(line)))
                    .getOrElse(folded);
        }

        /**
         * Whether nothing is shown.
         */
        boolean isBlank() {
            return diff.isBlank() && note.isEmpty();
        }

        /**
         * The text shown, as the plain view draws it.
         */
        public String text() {
            return diff.text() + note.map(line -> line + "\n").getOrElse("");
        }

        /**
         * The diff shown parsed, as the rich view draws it; parsed when it was read, but for the note.
         */
        public ParsedDiff parsed() {
            return folded().diff();
        }
    }

    /**
     * The left pane whose highlight the content panel shows on the repository screen. Its areas are resolved to it
     * by {@link ContentArea#pane}.
     */
    private enum Mirrored {
        FILES(FilesPanel.ID),
        BRANCHES(BranchesPanel.ID),
        COMMITS(CommitsPanel.ID),
        STASH(StashPanel.ID);

        private final String id;

        Mirrored(String id) {
            this.id = id;
        }

        static Option<Mirrored> of(PaneId pane) {
            return List.of(values()).find(mirrored -> mirrored.id.equals(pane.value()));
        }
    }

    /**
     * What the content panel shows, as {@link #filtered} gives it.
     *
     * @param contents  what each of its areas shows
     * @param allHidden whether the diff filters hide all the changes they show; none when one shows no diff
     */
    public record Filtered(List<ScrolledContent> contents, Option<Boolean> allHidden) {}
}
