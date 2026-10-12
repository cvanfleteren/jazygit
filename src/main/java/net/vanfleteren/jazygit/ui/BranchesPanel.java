package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.spinner.SpinnerStyle;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
    // The list's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;
    private final Keymap keymap = Keymap.standard();
    // The branches being pushed; the rows are built from it on every render.
    private Set<String> pushing = Set.of();

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
     * The frame of the push spinner to show at {@code now}.
     */
    static String spinnerFrame(Instant now) {
        return SPINNER.frame((int) (now.toEpochMilli() / SPINNER_FRAME_MILLIS % SPINNER.frameCount()));
    }

    private static LoadableList.Row row(Branch b, Duration since, Option<String> spinner) {
        return new LoadableList.Row(
                List.of(LoadableList.Seg.of("%4s ".formatted(age(since)) + (b.current() ? "* " : "  ") + b.name()))
                        .appendAll(sync(b))
                        .appendAll(spinner.map(frame -> LoadableList.Seg.styled(" " + frame, Theme.Classes.SPINNER))));
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
                                pushing.contains(b.name()) ? Option.of(spinnerFrame(clock.instant())) : Option.none())),
                BranchesPanel::reselect);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        focused = ID.equals(focusedId);
        pushing = model.pushing();
        return list.render(model.branches(), focused);
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
        return list.shown() instanceof Loadable.Loaded<List<Branch>>(List<Branch> branches) && !branches.isEmpty()
                ? Option.of(branches.get(Math.min(selectedIndex(), branches.size() - 1)).name())
                : Option.none();
    }

    /**
     * The message to send when the highlighted branch is not the one whose log {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return selectedBranch()
                .filter(branch -> !model.branchLog().map(BranchLog::branch).equals(Option.of(branch)))
                .map(SelectionMsg.BranchSelected::new);
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        return Option.of(event)
                .filter(e -> focused)
                .flatMap(e -> keymap.handle(Scopes.BRANCHES, e, selectedBranch()))
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }
}
