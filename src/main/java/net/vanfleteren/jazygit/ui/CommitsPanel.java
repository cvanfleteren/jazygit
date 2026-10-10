package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.commit.RewordMsg;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.help.HelpTopic;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Left-side panel listing the commit log.
 */
public class CommitsPanel {

    public static final String ID = "commits";

    private final LoadableList<List<Commit>> list = new LoadableList<>(Messages.get("panel.commits.title"), ID,
            commits -> commits.stream()
                    .map(c -> LoadableList.Row.of(c.shortSha() + " " + c.message()))
                    .toList());
    // The list's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;

    public CommitsPanel() {
        this(msg -> {
        });
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitsPanel(Consumer<Msg> dispatch) {
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
    public Optional<Commit> selectedCommit() {
        return list.shown() instanceof Loadable.Loaded<List<Commit>>(List<Commit> commits) && !commits.isEmpty()
                ? Optional.of(commits.get(Math.min(selectedIndex(), commits.size() - 1)))
                : Optional.empty();
    }

    /**
     * The message to send when the highlighted commit is not the one whose changes {@code model} holds.
     */
    public Optional<Msg> selectionChange(Model model) {
        return selectedCommit()
                .map(Commit::sha)
                .filter(sha -> !model.commitDetail().map(CommitDetail::sha).equals(Optional.of(sha)))
                .map(SelectionMsg.CommitSelected::new);
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        return Optional.of(event)
                .filter(e -> focused)
                .flatMap(this::request)
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private Optional<Msg> request(KeyEvent event) {
        return switch (event.string()) {
            case "?" -> Optional.of(new HelpMsg.Requested(HelpTopic.COMMITS));
            case "r" -> Optional.of(new RewordMsg.Requested(selectedIndex()));
            default -> Optional.empty();
        };
    }
}
