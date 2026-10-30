package net.vanfleteren.jazygit.ui;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.ScrolledContent;

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
        return switch (mirrored(model, focusedId)) {
            case FILES ->
                model.repoView()
                        .commitFiles()
                        .map(view -> commitFiles(model, view))
                        .getOrElse(() -> fileDiffs(model));
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
     * {@code title}, saying that changes to whitespace only are left out when they are.
     */
    static String titled(String title, DiffOptions options) {
        return options.ignoreWhitespace() ? Messages.get("panel.ignoringWhitespace", title) : title;
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
                        .map(area -> area.pane(model))
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
                : model.repoView()
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
                                sha -> view.commit(model, sha),
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
                .getOrElse(() -> loading(Messages.get("panel.log.title")));
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
        return view.diff()
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
                                        new ScrolledContent.FileChanges(
                                                view.of().sha(), diff.key(), changes),
                                        new Body.Diff(
                                                shownChanges(changes, options),
                                                model.preferences().diffLook()))))
                .getOrElse(() -> whenLoaded(
                        title,
                        view.files(),
                        files -> files.isEmpty() ? dim(title, Messages.get("content.noChanges")) : loading(title)));
    }

    /**
     * How the commit, the stash or the pull request {@code of} is named: by its short sha, by its {@code stash@{n}},
     * or by its {@code #n}.
     */
    static String changesName(Model model, CommitFilesView.Changes of) {
        return switch (of) {
            case CommitFilesView.Changes.OfCommit(Sha sha) -> sha.shortSha();
            case CommitFilesView.Changes.OfStash(Sha sha) ->
                stash(model, sha).map(Stash::ref).getOrElse(sha::shortSha);
            case CommitFilesView.Changes.OfPullRequest(var commits) -> "#" + commits.number();
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
         * commit or a stash changed in some of its files.
         */
        record Diff(ShownDiff diff, DiffLook look) implements Body {}

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
     * A diff as it is shown: {@code diff}, then the line {@code note}, which git did not print, if there is one.
     */
    record ShownDiff(DiffText diff, Option<String> note) {

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
            return note.map(diff.parsed()::withNote).getOrElse(diff.parsed());
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
