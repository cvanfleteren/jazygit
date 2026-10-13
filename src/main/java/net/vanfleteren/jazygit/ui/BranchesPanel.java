package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.widgets.spinner.SpinnerStyle;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Left-side panel listing the local branches, with the current branch marked. Space checks out
 * the highlighted branch, P pushes it, d asks where to delete it, n asks for the name of a new branch starting at it.
 */
public class BranchesPanel {

    public static final String ID = Scopes.BRANCHES.id();

    private static final SpinnerStyle SPINNER = SpinnerStyle.BOUNCING_BAR;
    private static final long SPINNER_FRAME_MILLIS = 120;

    private final LoadableList<List<Branch>> list;
    private final Keymap keymap = Keymap.standard();
    // The branches an operation runs on, shown with a spinner; the rows are built from it on every render.
    private Map<String, Operation> busy = HashMap.empty();

    /**
     * The time since the last commit as a number and one unit: s, m, h or d.
     */
    static String age(Duration since) {
        long seconds = Math.max(0, since.toSeconds());
        return seconds < 60 ? Messages.get("branches.age.seconds", String.valueOf(seconds))
                : seconds < 3600 ? Messages.get("branches.age.minutes", String.valueOf(seconds / 60))
                : seconds < 86400 ? Messages.get("branches.age.hours", String.valueOf(seconds / 3600))
                : Messages.get("branches.age.days", String.valueOf(seconds / 86400));
    }

    /**
     * The frame of the spinner of a busy branch to show at {@code now}.
     */
    static String spinnerFrame(Instant now) {
        return SPINNER.frame((int) (now.toEpochMilli() / SPINNER_FRAME_MILLIS % SPINNER.frameCount()));
    }

    /**
     * What a busy branch shows on the left of its spinner: what is being done to it, for the operations that
     * talk to its remote.
     */
    static Option<String> busyLabel(Operation operation) {
        return switch (operation) {
            case PUSH -> Option.of(Messages.get("branches.busy.push"));
            case PULL, FAST_FORWARD -> Option.of(Messages.get("branches.busy.pull"));
            default -> Option.none();
        };
    }

    /**
     * @param spinner the label and the spinner frame of a busy branch, as one text
     */
    private static LoadableList.Row row(Branch b, Duration since, Option<String> spinner) {
        return new LoadableList.Row(
                List.of(LoadableList.Seg.of("%4s ".formatted(age(since)) + (b.current() ? "* " : "  ") + b.name()))
                        .appendAll(sync(b))
                        .appendAll(spinner.map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.SPINNER))));
    }

    /**
     * The spinner of a branch that {@code operation} runs on, preceded by its label if it has one.
     */
    static String spinner(Operation operation, Instant now) {
        return busyLabel(operation).map(label -> label + " ").getOrElse("") + spinnerFrame(now);
    }

    /**
     * The commits not yet pushed in green and not yet pulled in red, as an arrow and a count each,
     * with a leading space; nothing for a branch that is in sync.
     */
    static List<LoadableList.Seg> sync(Branch branch) {
        return List.of(
                        Option.of(branch.ahead()).filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↑" + n, Theme.Classes.AHEAD)),
                        Option.of(branch.behind()).filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↓" + n, Theme.Classes.BEHIND)))
                .flatMap(o -> o);
    }

    /**
     * The selection follows its branch when the list is reordered. After a checkout it goes to the new
     * current branch, which is listed first.
     */
    private static int reselect(List<Branch> previous, List<Branch> next, int selected) {
        Option<String> before = current(previous);
        Option<String> target = before.equals(current(next))
                ? Option.of(selected).filter(i -> i < previous.size()).map(i -> previous.get(i).name())
                : current(next);
        return target
                .map(name -> next.indexWhere(b -> b.name().equals(name)))
                .filter(i -> i >= 0)
                .getOrElse(selected);
    }

    private static Option<String> current(List<Branch> branches) {
        return branches.find(Branch::current).map(Branch::name);
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public BranchesPanel(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemUTC());
    }

    /**
     * @param clock tells how long ago the branches were last committed to
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock) {
        list = new LoadableList<>(Messages.get("panel.branches.title"), ID,
                branches -> branches
                        .map(b -> row(b, Duration.between(b.tipTime(), clock.instant()),
                                busy.get(b.name()).map(operation -> spinner(operation, clock.instant())))),
                BranchesPanel::reselect);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.BRANCHES, event, selectedBranch()), dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        busy = model.busy();
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
    public Option<String> selectedBranch() {
        return list.selected(branches -> branches).map(Branch::name);
    }

    /**
     * The message to send when the highlighted branch is not the one whose log {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(selectedBranch(), model.branchLog(), SelectionMsg.BranchSelected::new);
    }
}
