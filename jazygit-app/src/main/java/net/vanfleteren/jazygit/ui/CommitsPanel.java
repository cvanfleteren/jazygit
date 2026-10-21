package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CommitSearch;
import net.vanfleteren.jazygit.state.CommitSearchMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * Left-side panel listing the commit log, or what the search of it found. {@code /} opens the line to type the
 * search in above the list, which then takes the keys that edit it; Enter runs it and Escape closes it. While the
 * panel lists the commits found, Escape clears the search.
 */
public class CommitsPanel {

    public static final String ID = Scopes.COMMITS.id();
    private static final int INITIALS_WIDTH = 2;
    private static final Pattern LETTER = Pattern.compile("\\X");

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();
    private final FilterLine searchLine = new FilterLine();
    // What the last render showed: the text of the open search line, and whether a search was listed.
    private Option<String> typing = Option.none();
    private boolean searching;

    /**
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param openCommit called when Enter is pressed on a commit, to move focus to its content
     */
    public CommitsPanel(Consumer<Msg> dispatch, Runnable openCommit) {
        this(dispatch, openCommit, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted commit is shown, also when the pane is not in use
     */
    public CommitsPanel(Consumer<Msg> dispatch, Runnable openCommit, Theme.Highlights highlights) {
        list = new LoadableList<>(Messages.get("panel.commits.title"), ID, CommitsPanel::reselect, highlights);
        list.onKey(event -> typing.map(text -> typed(text, event, dispatch))
                .orElse(() -> event.code() == KeyCode.ENTER && selectedCommit().isDefined()
                        ? Option.of(openCommit)
                        : searching && event.code() == KeyCode.ESCAPE
                                ? PaneKeys.sending(Option.of(new CommitSearchMsg.Cleared()), dispatch)
                                : PaneKeys.sending(keymap.handle(Scopes.COMMITS, event, selectedCommit()), dispatch)));
    }

    /**
     * What {@code event} does while the search line is open with {@code text}: the keys that edit it do, Enter runs
     * it, Escape closes it, and the other keys do nothing, but for those the list moves with, which it took before.
     */
    private Runnable typed(String text, KeyEvent event, Consumer<Msg> dispatch) {
        Option<Msg> msg = event.code() == KeyCode.ESCAPE
                ? Option.of(new CommitSearchMsg.Cancelled())
                : event.code() == KeyCode.ENTER
                        ? Option.of(new CommitSearchMsg.Submitted())
                        : searchLine.edit(text, event).map(CommitSearchMsg.Edited::new);
        return () -> msg.forEach(dispatch);
    }

    /**
     * The selection follows its commit when commits come on top, except on the newest one: it stays there, so a
     * new commit gets highlighted.
     */
    private static int reselect(List<Commit> previous, List<Commit> next, int selected) {
        return selected == 0 ? 0 : LoadableList.Reselect.following(Commit::sha).apply(previous, next, selected);
    }

    static List<LoadableList.Row> rows(List<CommitGraph.Node> nodes) {
        return rows(nodes, Option.none());
    }

    /**
     * @param copied the commit copied to cherry-pick, whose sha stands out
     */
    static List<LoadableList.Row> rows(List<CommitGraph.Node> nodes, Option<Sha> copied) {
        // Each commit joins the branch that the commit before it closes.
        return nodes.zipWith(
                nodes.map(CommitGraph.Node::closesBranch).prepend(false),
                (node, joins) -> row(node, joins, copied.contains(node.commit().sha())));
    }

    /**
     * The rows of the commits a search found, without the rails of merges: with commits left out, they no longer
     * join up. A merge is still a {@code ◎}.
     */
    static List<LoadableList.Row> foundRows(List<Commit> commits, Option<Sha> copied) {
        return commits.map(commit -> row(commit, commit.isMerge() ? "◎ " : "○ ", copied.contains(commit.sha())));
    }

    /**
     * The row of a commit. Commits brought in by a merge are drawn on a rail beside the merge, which opens
     * it with {@code ◎─╮}; the commit after the branch, where it joins the main line, is {@code ○─╯}.
     *
     * @param joins whether the branch before this commit joins the main line at it
     */
    static LoadableList.Row row(CommitGraph.Node node, boolean joins) {
        return row(node, joins, false);
    }

    /**
     * @param copied whether the commit is copied to cherry-pick, which shows in its sha
     */
    static LoadableList.Row row(CommitGraph.Node node, boolean joins, boolean copied) {
        Commit commit = node.commit();
        String rails = "│ ".repeat(node.depth());
        return row(commit, commit.isMerge() ? rails + "◎─╮ " : joins ? rails + "○─╯ " : rails + "○ ", copied);
    }

    /**
     * @param graph where the commit is in the graph, before its message
     */
    private static LoadableList.Row row(Commit commit, String graph, boolean copied) {
        String author = " " + initials(commit.authorName()) + " " + graph;
        List<LoadableList.Seg> prefix = copied
                ? List.of(LoadableList.Seg.styled(commit.shortSha(), Theme.Classes.COPIED), LoadableList.Seg.of(author))
                : List.of(LoadableList.Seg.of(commit.shortSha() + author));
        List<LoadableList.Seg> tags =
                commit.tags().map(tag -> LoadableList.Seg.styled("[" + tag.name() + "] ", Theme.Classes.TAG));
        return new LoadableList.Row(prefix.appendAll(tags).append(LoadableList.Seg.of(commit.message())));
    }

    /**
     * The capitalized first letters of the first two words of {@code name}; a name of a single word gives
     * its first two letters as they are. No more than two columns: a CJK or emoji letter takes both.
     */
    static String initials(String name) {
        List<String> words = List.of(name.strip().split("\\s+")).filter(word -> !word.isEmpty());
        if (words.size() < 2) {
            return TextWidth.start(name.strip(), INITIALS_WIDTH);
        }
        return TextWidth.start(
                words.take(2).map(word -> firstLetter(word).toUpperCase()).mkString(), INITIALS_WIDTH);
    }

    /**
     * The first letter of {@code word} as it is seen: a whole emoji or flag, with the marks on it.
     */
    private static String firstLetter(String word) {
        Matcher letter = LETTER.matcher(word);
        return letter.lookingAt() ? letter.group() : "";
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return render(model, focusedId, focusedId);
    }

    /**
     * @param activeId the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        Option<Sha> copied = model.repoView().cherryPick().map(Commit::sha);
        Option<CommitSearch> search = model.repoView().commitSearch();
        typing = model.repoView().searchLine();
        searching = search.isDefined();
        list.whenEmpty(search.map(found -> Messages.get("panel.commits.noMatches")));
        return list.render(
                search.map(CommitSearch::results).getOrElse(model.repo().commits()),
                commits -> search.isDefined() ? foundRows(commits, copied) : rows(CommitGraph.layout(commits), copied),
                ID.equals(focusedId),
                ID.equals(activeId),
                Pane.title(search.map(CommitsPanel::title).getOrElse(() -> Messages.get("panel.commits.title"))),
                typing.map(searchLine::field));
    }

    /**
     * The title of the panel while it lists what {@code search} found, which says when it found more than it
     * lists.
     */
    static String title(CommitSearch search) {
        return search.results().toOption().exists(found -> found.size() >= GitInfoProvider.MAX_COMMITS)
                ? Messages.get("panel.commits.titleSearchCapped", search.text(), GitInfoProvider.MAX_COMMITS)
                : Messages.get("panel.commits.titleSearch", search.text());
    }

    /**
     * The index of the currently highlighted commit, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The highlighted commit, once the commits are shown.
     */
    public Option<Commit> selectedCommit() {
        return list.selected(commits -> commits);
    }

    /**
     * The message to send when the highlighted commit is not the one whose changes {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(
                selectedCommit().map(Commit::sha), model.repoView().commitDetail(), SelectionMsg.CommitSelected::new);
    }
}
