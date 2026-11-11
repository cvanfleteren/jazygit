package net.vanfleteren.jazygit.ui;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
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
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.Collapsed;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.diff.WholeFile;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;

/**
 * What the content panel shows in a model, apart from how it is drawn: it mirrors whichever left pane is in use,
 * showing the file diff, the branch log, the commit diff/body or what a stash stashed; on the pull requests screen,
 * the highlighted pull request.
 */
final class ContentView {

    private static final DiffText NO_CHANGES = DiffText.of("");

    private ContentView() {}

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    static Shown of(Model model, String focusedId) {
        if (model.screen() == Screen.PULL_REQUESTS) {
            return pullRequest(model);
        }
        return expanded(model, mirrored(model, focusedId));
    }

    /**
     * What the repository screen shows next to the pane {@code mirrored} has highlighted.
     */
    private static Shown repository(Model model, Mirrored mirrored) {
        return switch (mirrored) {
            case FILES ->
                model.repoView()
                        .commitFiles()
                        .map(view -> commitFiles(model, view))
                        .getOrElse(() -> model.repoView()
                                .unchanged()
                                .map(node -> unchanged(model, node))
                                .getOrElse(() -> fileDiffs(model)));
            case BRANCHES -> branches(model);
            case COMMITS ->
                commit(
                        model,
                        ContentArea.COMMIT,
                        model.repoView().commitDetail(),
                        model::commit,
                        Option.when(model.repo().commits().toOption().exists(List::isEmpty), "content.noCommits"));
            case STASH -> stash(model);
        };
    }

    /**
     * What the repository screen shows next to the pane {@code mirrored} has highlighted, each diff with the lines
     * expanded in the area it is shown in (ADR 77).
     */
    private static Shown expanded(Model model, Mirrored mirrored) {
        return switch (repository(model, mirrored)) {
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
        Body body = switch (area.body()) {
            case Body.Diff(ShownDiff diff, DiffLook look, var remarks) ->
                new Body.Diff(diff.expanded(expansion, context).versioned(version), look, remarks);
            case Body.CommitChanges(Commit commit, ShownDiff changes, DiffLook look) ->
                new Body.CommitChanges(
                        commit, changes.expanded(expansion, context).versioned(version), look);
            case Body.StashChanges(Stash stash, ShownDiff changes, DiffLook look) ->
                new Body.StashChanges(
                        stash, changes.expanded(expansion, context).versioned(version), look);
            case Body other -> other;
        };
        // A file without changes has one version: the title says only whether it is rendered (ADR 108).
        String title = area.content() instanceof ScrolledContent.Unchanged
                ? version.exists(Version::rendered) ? Messages.get("panel.file.rendered", area.title()) : area.title()
                : version.map(shown -> versioned(area.title(), shown)).getOrElse(area.title());
        return new Shown.Area(area.area(), title, area.content(), body);
    }

    /**
     * The version a file without changes shows of itself, as source, when none was asked for: it is read whole, so
     * it shows with no line left out, and as Ctrl+T renders it (ADR 108).
     */
    private static Option<Version> asVersion(Model model, ScrolledContent content) {
        return content instanceof ScrolledContent.Unchanged(String path, DiffText asDiff)
                ? Option.of(new Version(
                        content,
                        path,
                        Option.of(FileVersion.NEW),
                        new WholeFile(
                                content,
                                new WholeFile.Source.OnDisk(path),
                                path,
                                model.preferences().diffOptions()),
                        new Loadable.Loaded<>(asDiff)))
                : Option.none();
    }

    /**
     * The node without changes highlighted among all files of the working tree (ADR 108): what a file holds, read
     * from disk, or how many files a directory holds. The file shown before stays while another is read.
     */
    private static Shown unchanged(Model model, UnchangedNode node) {
        String title = Messages.get("panel.file.title");
        return switch (node) {
            case UnchangedNode.Directory(String path, int files) ->
                dim(title, Messages.get("content.directoryFiles", path.isEmpty() ? "/" : path, files));
            case UnchangedNode.File(var content) -> {
                Preview<String, FileContent> shown = content.shown();
                yield whenLoaded(title, shown.value(), read -> fileContent(model, shown.key(), read, title));
            }
        };
    }

    /**
     * What the file at {@code path} holds, {@code content}: text as a diff of itself, drawn in the rich view whatever
     * look diffs have, and anything else as a line that says what it is.
     */
    private static Shown fileContent(Model model, String path, FileContent content, String title) {
        return switch (content) {
            case FileContent.Text(DiffText asDiff) -> {
                DiffLook look = model.preferences().diffLook();
                yield new Shown.Area(
                        ContentArea.FILE_CONTENT,
                        title,
                        new ScrolledContent.Unchanged(path, asDiff),
                        new Body.Diff(shown(asDiff), new DiffLook(DiffStyle.RICH, look.syntax(), look.words())));
            }
            case FileContent.Binary(long size) -> dim(title, Messages.get("content.file.binarySize", size(size)));
            case FileContent.Link(String target) -> dim(title, Messages.get("content.file.link", target));
            case FileContent.Submodule(var commit) ->
                dim(
                        title,
                        commit.map(sha -> Messages.get("content.file.submodule", sha.shortSha()))
                                .getOrElse(() -> Messages.get("content.file.submoduleUnknown")));
        };
    }

    /**
     * {@code bytes} as a size to read: in bytes, KiB or MiB.
     */
    static String size(long bytes) {
        return bytes < 1024
                ? bytes + " B"
                : bytes < 1024 * 1024
                        ? (bytes + 512) / 1024 + " KiB"
                        : String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    /**
     * {@code title}, saying which version of its file the area shows in place of its diff, if it shows one, from the
     * moment it was asked for, and whether rendered (ADR 103): while the file is read, or when it could not be, it says
     * so too (ADR 102).
     */
    static String versioned(String title, Version version) {
        return version.shown()
                .map(shown -> Messages.get(
                        shown == FileVersion.NEW
                                ? version.rendered() ? "panel.version.newRendered" : "panel.version.new"
                                : version.rendered() ? "panel.version.originalRendered" : "panel.version.original",
                        title))
                // A read that has no such file shows the diff, as a failed one does, and says so as well.
                .map(named -> version.failed()
                        ? Messages.get("panel.version.failed", named)
                        : version.whole() instanceof Loadable.Loading<DiffText>
                                ? Messages.get("panel.version.reading", named)
                                : named)
                .getOrElse(title);
    }

    /**
     * {@code diff}, with a line saying it is truncated when it was cut off.
     */
    static ShownDiff shown(DiffText diff) {
        return new ShownDiff(diff, Option.when(diff.truncated(), () -> Messages.get("git.diff.truncated")));
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
        DiffContext context = options.context();
        String withContext = context.equals(DiffContext.DEFAULT)
                ? title
                : context.wholeFile()
                        ? Messages.get("panel.wholeFile", title)
                        : Messages.get("panel.context", title, context.lines());
        return options.ignoreWhitespace() ? Messages.get("panel.ignoringWhitespace", withContext) : withContext;
    }

    /**
     * The pane {@code focusedId} is or belongs to; while a popup has the focus, the pane, or the pane of the area,
     * it was opened from. Nothing has the focus before the first render.
     */
    private static Mirrored mirrored(Model model, String focusedId) {
        Option<String> id = ContentArea.focusable(focusedId).isDefined()
                        || Mirrored.of(focusedId).isDefined()
                ? Option.of(focusedId)
                : model.repoView().focusedPane().orElse(Option.of(focusedId));
        return id.map(known -> ContentArea.focusable(known)
                        .map(area -> area.pane(model).value())
                        .getOrElse(known))
                .flatMap(Mirrored::of)
                .getOrElse(Mirrored.FILES);
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
        String title = Messages.get("panel.pullRequest.title");
        return model.prOverview()
                .preview()
                .map(Preview::shown)
                .map(preview -> whenLoaded(
                        title,
                        preview.value(),
                        read -> new Shown.Area(
                                ContentArea.PULL_REQUEST,
                                Messages.get(
                                        "panel.pullRequest.titleFor",
                                        preview.key().repository(),
                                        String.valueOf(preview.key().number())),
                                new ScrolledContent.PullRequest(preview.key(), read),
                                new Body.PullRequest(preview.key(), read))))
                .getOrElse(() -> dim(title, Messages.get("content.noPullRequest")));
    }

    /**
     * One area for the staged changes and one for the unstaged changes, one above the other when both exist.
     */
    private static Shown fileDiffs(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        String title = titled(Messages.get("panel.diff.title"), options);
        // With no files there is nothing to highlight, so no diff is ever requested.
        return model.repo().status().toOption().exists(status -> status.files().isEmpty())
                ? dim(title, Messages.get("content.noChangedFiles"))
                : model.repo().status().toOption().exists(status -> FilesPanel.noneMatch(model, status.files()))
                        ? dim(title, Messages.get("panel.files.noMatches"))
                        : model.repoView()
                                .fileDiff()
                                .map(Preview::shown)
                                .map(diff -> whenLoaded(
                                        title,
                                        diff.value(),
                                        diffs -> diffAreas(
                                                options, model.preferences().diffLook(), title, diffs)))
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
                        new Body.Diff(shown(area._3), look))));
    }

    /**
     * The commit highlighted in the divergence the local branches pane lists, or in the commits of a branch the pane
     * lists, or else the log of the branch.
     */
    private static Shown branches(Model model) {
        return model.repoView()
                .divergence()
                .filter(view -> model.repoView().branchesTab() == BranchesTab.LOCAL)
                .map(view -> commit(
                        model,
                        ContentArea.DIVERGENCE,
                        view.detail(),
                        view::commit,
                        Option.when(view.commits().toOption().exists(Divergence::isEmpty), "content.noDivergence")))
                .orElse(() -> model.repoView()
                        .branchCommits()
                        .map(view -> commit(
                                model,
                                ContentArea.BRANCH_COMMIT,
                                view.detail(),
                                sha -> model.branchCommit(view, sha),
                                Option.when(
                                        view.commits(model).toOption().exists(List::isEmpty), "content.noCommits"))))
                .getOrElse(() -> branchLog(model));
    }

    private static Shown branchLog(Model model) {
        return model.repoView()
                .branchLog()
                .map(Preview::shown)
                .map(log -> {
                    String title = Messages.get("panel.log.titleFor", log.key().name());
                    AheadView ahead = new AheadView(
                            AheadUpdate.branch(model, log.key()),
                            AheadUpdate.distance(model, log.key()),
                            model.repoView().onlyAhead());
                    return whenLoaded(ahead.title(title), log.value(), commits -> {
                        List<Commit> shown = ahead.shown(commits);
                        return new Shown.Area(
                                ContentArea.LOG,
                                ahead.title(title),
                                new ScrolledContent.BranchLog(shown),
                                new Body.BranchLog(shown, ahead));
                    });
                })
                .getOrElse(() -> model.repoView()
                        .unfetched()
                        .map(pullRequest -> dim(
                                Messages.get("panel.log.title"),
                                Messages.get("content.unfetched", String.valueOf(pullRequest))))
                        .getOrElse(() -> loading(Messages.get("panel.log.title"))));
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
        String title = Messages.get("panel.commit.title");
        return detail.map(Preview::shown)
                .map(shown -> whenLoaded(
                        title,
                        shown.value(),
                        changes -> commit.apply(shown.key())
                                .<Shown>map(found -> commitArea(
                                        area,
                                        found,
                                        changes,
                                        options,
                                        model.preferences().diffLook()))
                                .getOrElse(() -> loading(title))))
                .getOrElse(() -> nothing.map(message -> dim(title, Messages.get(message)))
                        .getOrElse(() -> loading(title)));
    }

    private static Shown.Area commitArea(
            ContentArea area, Commit commit, DiffText changes, DiffOptions options, DiffLook look) {
        return new Shown.Area(
                area,
                titled(Messages.get("panel.commit.titleFor", commit.shortSha()), options),
                new ScrolledContent.CommitChanges(commit, changes),
                new Body.CommitChanges(commit, shownChanges(changes, options), look));
    }

    /**
     * What the commit or the stash whose files the files pane lists changed in the files of the node highlighted in
     * them; what was shown before stays while it is read. Before a node is highlighted, it is loading, or says that
     * nothing changed.
     */
    private static Shown commitFiles(Model model, CommitFilesView view) {
        DiffOptions options = model.preferences().diffOptions();
        String name = changesName(model, view.of());
        String title = titled(Messages.get("panel.commitFiles.titleFor", name), options);
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
                                : new Shown.Area(
                                        ContentArea.COMMIT_FILE,
                                        titled(
                                                Messages.get("panel.commitFile.titleFor", name, nodePath(diff.key())),
                                                options),
                                        new ScrolledContent.FileChanges(view.of(), diff.key(), changes),
                                        new Body.Diff(
                                                shownChanges(changes, options),
                                                model.preferences().diffLook(),
                                                remarks(model, view, diff.key())))))
                .getOrElse(() -> whenLoaded(
                        title,
                        view.files(),
                        files -> files.isEmpty() ? dim(title, Messages.get("content.noChanges")) : loading(title))));
    }

    /**
     * The review threads of the pull request {@code view} lists the files of, as they are shown with its diff of
     * {@code files}; none for a commit or a stash, and while they are first read. The time they are told to have been
     * written against is only that of the clock when one of them is on those files: it is part of what the content
     * panel keeps its styled diff by, so it would style a diff without them again every minute.
     */
    private static Option<ShownRemarks> remarks(Model model, CommitFilesView view, List<CommitFile> files) {
        return view.remarks().flatMap(remarks -> switch (remarks) {
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
        });
    }

    /**
     * How the commit, the stash, the pull request or the branch {@code of} is named: by its short sha, by its
     * {@code stash@{n}}, by its {@code #n}, or by its name and the base it is compared with (ADR 61).
     */
    static String changesName(Model model, Changes of) {
        return switch (of) {
            case Changes.OfCommit(Sha sha) -> sha.shortSha();
            case Changes.OfStash(Sha sha) -> stash(model, sha).map(Stash::ref).getOrElse(sha::shortSha);
            case Changes.OfPullRequest(var commits) -> "#" + commits.number();
            case Changes.OfBranch(var branch, var base, var sha) ->
                Messages.get(
                        "panel.commitFiles.branch", branch.value(), base.ref().name());
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
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("panel.stash.title");
        return model.repoView()
                .stashDetail()
                .map(Preview::shown)
                .flatMap(detail -> stash(model, detail.key())
                        .map(stash -> whenLoaded(
                                title,
                                detail.value(),
                                (DiffText changes) -> new Shown.Area(
                                        ContentArea.STASH,
                                        titled(Messages.get("panel.stash.titleFor", stash.ref()), options),
                                        new ScrolledContent.StashChanges(stash.sha(), changes),
                                        new Body.StashChanges(
                                                stash,
                                                shownChanges(changes, options),
                                                model.preferences().diffLook())))))
                .getOrElse(() -> whenLoaded(
                        title,
                        model.repo().stashes(),
                        stashes -> stashes.isEmpty() ? dim(title, Messages.get("content.noStashes")) : loading(title)));
    }

    private static Option<Stash> stash(Model model, Sha sha) {
        return model.repo()
                .stashes()
                .toOption()
                .flatMap(stashes -> stashes.find(stash -> stash.sha().equals(sha)));
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
    sealed interface Shown {

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
    sealed interface Body {

        /**
         * A unified diff, drawn as {@code look} says: the staged or the unstaged changes of the working tree, or what a
         * commit, a stash or a pull request changed in some of its files, with the review threads of a pull request
         * (ADR 55).
         */
        record Diff(ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks) implements Body {

            /**
             * A diff without review threads.
             */
            Diff(ShownDiff diff, DiffLook look) {
                this(diff, look, Option.none());
            }
        }

        /**
         * The log of a branch, as far as {@code ahead} shows it.
         */
        record BranchLog(List<Commit> commits, AheadView ahead) implements Body {}

        /**
         * A commit and what it changed, drawn as {@code look} says.
         */
        record CommitChanges(Commit commit, ShownDiff changes, DiffLook look) implements Body {}

        /**
         * A stash and what it stashed, drawn as {@code look} says.
         */
        record StashChanges(Stash stash, ShownDiff changes, DiffLook look) implements Body {}

        /**
         * A pull request, and what it says and changes.
         */
        record PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) implements Body {}
    }

    /**
     * The review threads of a pull request as they are shown with a diff of some of its files (ADR 55).
     *
     * @param threads the threads read
     * @param now     the time it is, to the minute, to tell how long ago a comment was written: the diff is only
     *                styled again once a minute for it
     * @param failed  why reading them failed, when it did and none were read before
     * @param onLines whether they were read at the commits of the diff, so their lines number its lines: else the
     *                threads on a line are listed at the end of their file
     * @param collapsed which threads and comments are collapsed (ADR 62)
     * @param sending the ids of the threads a reply or a resolving is sent for (ADR 60)
     * @param deleting the ids of the comments being deleted (ADR 64)
     * @param pictures the pictures of the images of the threads that were read, by where they are (ADR 84)
     */
    record ShownRemarks(
            List<ReviewThread> threads,
            Instant now,
            Option<Failure> failed,
            boolean onLines,
            Collapsed collapsed,
            Set<ReviewThread.Id> sending,
            Set<ReviewThread.Comment.Id> deleting,
            Map<String, Picture> pictures) {

        ShownRemarks(
                List<ReviewThread> threads,
                Instant now,
                Option<Failure> failed,
                boolean onLines,
                Collapsed collapsed,
                Set<ReviewThread.Id> sending,
                Set<ReviewThread.Comment.Id> deleting) {
            this(threads, now, failed, onLines, collapsed, sending, deleting, HashMap.empty());
        }
    }

    /**
     * A diff as it is shown: {@code diff}, then the line {@code note}, which git did not print, if there is one, with
     * the lines {@code expansion} expanded around its hunks, folded to {@code context} lines (ADR 77), and its file
     * shown as the {@code version} of it the area shows, if it shows one (ADR 102).
     */
    record ShownDiff(
            DiffText diff,
            Option<String> note,
            Option<Expansion> expansion,
            DiffContext context,
            Option<Version> version) {

        /**
         * {@code diff} and its note, as git gives it.
         */
        ShownDiff(DiffText diff, Option<String> note) {
            this(diff, note, Option.none(), DiffContext.DEFAULT, Option.none());
        }

        /**
         * This diff with the lines of {@code expanded} shown, folded to {@code folding} lines of context (ADR 77).
         */
        ShownDiff expanded(Option<Expansion> expanded, DiffContext folding) {
            return new ShownDiff(diff, note, expanded, folding, version);
        }

        /**
         * This diff with its file shown as {@code shown} has it, if it shows a version of it (ADR 102).
         */
        ShownDiff versioned(Option<Version> shown) {
            return new ShownDiff(diff, note, expansion, context, shown);
        }

        /**
         * The diff shown parsed, its files read whole folded, with what is known of each of them (ADR 77), its file
         * replaced by the version of it shown, if one is (ADR 102).
         */
        Expansion.Folded folded() {
            Expansion.Folded expanded = expansion
                    .map(kept -> kept.folded(diff.parsed(), context))
                    .getOrElse(() -> Expansion.Folded.of(diff.parsed()));
            Expansion.Folded folded =
                    version.map(shown -> shown.applied(expanded)).getOrElse(expanded);
            return note.map(line -> new Expansion.Folded(folded.diff().withNote(line), folded.files()))
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
        String text() {
            return diff.text() + note.map(line -> line + "\n").getOrElse("");
        }

        /**
         * The diff shown parsed, as the rich view draws it; parsed when it was read, but for the note.
         */
        ParsedDiff parsed() {
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

        static Option<Mirrored> of(String id) {
            return List.of(values()).find(mirrored -> mirrored.id.equals(id));
        }
    }
}
