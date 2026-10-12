package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.Loadable;
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
    // The list's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;
    private final Keymap keymap = Keymap.standard();
    private final Runnable openCommit;

    public CommitsPanel() {
        this(msg -> {
        });
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitsPanel(Consumer<Msg> dispatch) {
        this(dispatch, () -> {
        });
    }

    /**
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param openCommit called when Enter is pressed on a commit, to move focus to its content
     */
    public CommitsPanel(Consumer<Msg> dispatch, Runnable openCommit) {
        this.openCommit = openCommit;
        list.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        focused = ID.equals(focusedId);
        return list.render(model.commits(), focused);
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
        return list.shown() instanceof Loadable.Loaded<List<Commit>>(List<Commit> commits) && !commits.isEmpty()
                ? Option.of(commits.get(Math.min(selectedIndex(), commits.size() - 1)))
                : Option.none();
    }

    /**
     * The message to send when the highlighted commit is not the one whose changes {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return selectedCommit()
                .map(Commit::sha)
                .filter(sha -> !model.commitDetail().map(CommitDetail::sha).equals(Option.of(sha)))
                .map(SelectionMsg.CommitSelected::new);
    }

    static List<LoadableList.Row> rows(List<CommitGraph.Node> nodes) {
        return List.range(0, nodes.size())
                .map(i -> row(nodes.get(i), i > 0 && nodes.get(i - 1).closesBranch()));
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
        return LoadableList.Row.of(commit.shortSha() + " " + initials(commit.authorName()) + " " + graph + commit.message());
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

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        if (focused && event.code() == KeyCode.ENTER && selectedCommit().isDefined()) {
            openCommit.run();
            return EventResult.HANDLED;
        }
        return Option.of(event)
                .filter(e -> focused)
                .flatMap(this::request)
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    private Option<Msg> request(KeyEvent event) {
        return keymap.handle(Scopes.COMMITS, event, Option.of(selectedIndex()));
    }
}
