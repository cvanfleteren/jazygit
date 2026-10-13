package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.function.Consumer;

/**
 * Left-side panel listing the commit log.
 */
public class CommitsPanel {

    public static final String ID = Scopes.COMMITS.id();

    private final LoadableList<List<Commit>> list = new LoadableList<>(Messages.get("panel.commits.title"), ID,
            commits -> rows(CommitGraph.layout(commits)));
    private final Keymap keymap = Keymap.standard();

    /**
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param openCommit called when Enter is pressed on a commit, to move focus to its content
     */
    public CommitsPanel(Consumer<Msg> dispatch, Runnable openCommit) {
        list.onKey(event -> event.code() == KeyCode.ENTER && selectedCommit().isDefined()
                ? Option.of(openCommit)
                : PaneKeys.sending(keymap.handle(Scopes.COMMITS, event, Option.of(selectedIndex())), dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return list.render(model.commits(), ID.equals(focusedId));
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
        return PaneKeys.selectionChange(selectedCommit().map(Commit::sha), model.commitDetail(),
                SelectionMsg.CommitSelected::new);
    }

    static List<LoadableList.Row> rows(List<CommitGraph.Node> nodes) {
        // Each commit joins the branch that the commit before it closes.
        return nodes.zipWith(nodes.map(CommitGraph.Node::closesBranch).prepend(false), CommitsPanel::row);
    }

    /**
     * The row of a commit. Commits brought in by a merge are drawn on a rail beside the merge, which opens
     * it with {@code ◎─╮}; the commit after the branch, where it joins the main line, is {@code ○─╯}.
     *
     * @param joins whether the branch before this commit joins the main line at it
     */
    static LoadableList.Row row(CommitGraph.Node node, boolean joins) {
        Commit commit = node.commit();
        String rails = "│ ".repeat(node.depth());
        String graph = commit.isMerge() ? rails + "◎─╮ " : joins ? rails + "○─╯ " : rails + "○ ";
        LoadableList.Seg prefix = LoadableList.Seg.of(commit.shortSha() + " " + initials(commit.authorName()) + " " + graph);
        List<LoadableList.Seg> tags = commit.tags()
                .map(tag -> LoadableList.Seg.styled("[" + tag + "] ", Theme.Classes.TAG));
        return new LoadableList.Row(tags.prepend(prefix).append(LoadableList.Seg.of(commit.message())));
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

}
