package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Left-side panel listing the local branches, with the current branch marked. Space checks out
 * the highlighted branch, n asks for the name of a new branch starting at it.
 */
public class BranchesPanel {

    public static final String ID = "branches";

    private final LoadableList<List<Branch>> list = new LoadableList<>("Branches", ID,
            branches -> branches.stream()
                    .map(b -> (b.current() ? "* " : "  ") + b.name())
                    .toList(),
            BranchesPanel::reselect);

    /**
     * The selection follows its branch when the list is reordered. After a checkout it goes to the new
     * current branch, which is listed first.
     */
    private static int reselect(List<Branch> previous, List<Branch> next, int selected) {
        Optional<String> before = current(previous);
        Optional<String> target = before.equals(current(next))
                ? Optional.of(selected).filter(i -> i < previous.size()).map(i -> previous.get(i).name())
                : current(next);
        return target
                .map(name -> IntStream.range(0, next.size()).filter(i -> next.get(i).name().equals(name)).boxed()
                        .findFirst()
                        .orElse(selected))
                .orElse(selected);
    }

    private static Optional<String> current(List<Branch> branches) {
        return branches.stream().filter(Branch::current).map(Branch::name).findFirst();
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public BranchesPanel(Consumer<Msg> dispatch) {
        list.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return list.render(model.branches(), ID.equals(focusedId));
    }

    /**
     * The index of the currently highlighted branch, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The name of the highlighted branch, once the branches are shown.
     */
    public Optional<String> selectedBranch() {
        return list.shown() instanceof Loadable.Loaded<List<Branch>>(List<Branch> branches) && !branches.isEmpty()
                ? Optional.of(branches.get(Math.min(selectedIndex(), branches.size() - 1)).name())
                : Optional.empty();
    }

    /**
     * The message to send when the highlighted branch is not the one whose log {@code model} holds.
     */
    public Optional<Msg> selectionChange(Model model) {
        return selectedBranch()
                .filter(branch -> !model.branchLog().map(BranchLog::branch).equals(Optional.of(branch)))
                .map(Msg.BranchSelected::new);
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        return selectedBranch()
                .flatMap(branch -> request(event, branch))
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private static Optional<Msg> request(KeyEvent event, String branch) {
        return event.isChar(' ') ? Optional.of(new Msg.CheckoutRequested(branch))
                : event.isChar('n') ? Optional.of(new Msg.NewBranchRequested(branch))
                : Optional.empty();
    }
}
