package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.SelectionMsg;

/**
 * Left-side panel listing the commit log.
 */
public class CommitsPanel {

    public static final String ID = Scopes.COMMITS.id();

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();

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
        list.onKey(event -> event.code() == KeyCode.ENTER && selectedCommit().isDefined()
                ? Option.of(openCommit)
                : PaneKeys.sending(keymap.handle(Scopes.COMMITS, event, selectedCommit()), dispatch));
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
        String graph = commit.isMerge() ? rails + "◎─╮ " : joins ? rails + "○─╯ " : rails + "○ ";
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
     * its first two letters as they are.
     */
    static String initials(String name) {
        List<String> words = List.of(name.strip().split("\\s+")).filter(word -> !word.isEmpty());
        if (words.size() < 2) {
            String word = name.strip();
            return word.substring(0, word.offsetByCodePoints(0, Math.min(2, word.codePointCount(0, word.length()))));
        }
        return words.take(2)
                .map(word -> word.substring(0, word.offsetByCodePoints(0, 1)).toUpperCase())
                .mkString();
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
        return list.render(
                model.repo().commits(),
                commits -> rows(CommitGraph.layout(commits), copied),
                ID.equals(focusedId),
                ID.equals(activeId));
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
