package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.*;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.git.model.Branch;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking where to delete a branch. An option is chosen with its letter, or with the arrow
 * keys and Enter; Escape cancels. Whether it is open lives in the {@link Model}.
 */
public final class DeleteBranchDialog {

    public static final String ID = "delete-branch";

    private static final int WIDTH = 40;

    /**
     * An option of the popup; no scope means cancel.
     */
    private record Option(char key, String labelKey, Optional<BranchCmd.DeleteBranch.DeleteScope> scope) {

        Msg msg() {
            return scope.<Msg>map(DeleteBranchMsg.Chosen::new).orElseGet(DeleteBranchMsg.Cancelled::new);
        }

        String row() {
            return Messages.get("dialog.deleteBranch.row", String.valueOf(key), Messages.get(labelKey));
        }

        /**
         * Whether the option acts on the remote branch, so it needs one.
         */
        boolean needsRemote() {
            return scope.filter(scope -> scope != BranchCmd.DeleteBranch.DeleteScope.LOCAL).isPresent();
        }

        boolean enabled(boolean remoteAvailable) {
            return remoteAvailable || !needsRemote();
        }
    }

    private static final List<Option> OPTIONS = List.of(
            new Option('d', "dialog.deleteBranch.local", Optional.of(BranchCmd.DeleteBranch.DeleteScope.LOCAL)),
            new Option('r', "dialog.deleteBranch.remote", Optional.of(BranchCmd.DeleteBranch.DeleteScope.REMOTE)),
            new Option('b', "dialog.deleteBranch.both", Optional.of(BranchCmd.DeleteBranch.DeleteScope.BOTH)),
            new Option('c', "dialog.deleteBranch.cancel", Optional.empty()));

    private final ListElement<?> list = list()
            .id(ID)
            .focusable()
            .highlightSymbol("")
            .onKeyEvent(this::handleKey);
    private final Consumer<Msg> dispatch;
    // What the rows show; null before the first render.
    private Boolean remoteAvailable;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public DeleteBranchDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks where to delete a branch.
     */
    public Optional<Element> render(Model model) {
        return model.popup(DeleteBranchPopup.class).map(DeleteBranchPopup::branch).map(branch -> {
            showOptions(remoteDeletable(model, branch));
            return branch;
        }).map(branch -> dialog(Messages.get("dialog.deleteBranch.title", branch), list)
                .rounded()
                .width(WIDTH)
                .padding(1)
                .onCancel(() -> choose(OPTIONS.getLast())));
    }

    private static boolean remoteDeletable(Model model, String branch) {
        return model.branches() instanceof Loadable.Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && b.remoteDeletable());
    }

    /**
     * Options that need a remote branch are struck through and dimmed when there is none.
     */
    private void showOptions(boolean available) {
        if (remoteAvailable == null || remoteAvailable != available) {
            remoteAvailable = available;
            list.elements(OPTIONS.stream()
                    .map(option -> option.enabled(available) ? text(option.row()) : text(option.row()).crossedOut().dim())
                    .toArray(StyledElement[]::new));
        }
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            choose(OPTIONS.getLast());
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            choose(OPTIONS.get(Math.clamp(list.selected(), 0, OPTIONS.size() - 1)));
            return EventResult.HANDLED;
        }
        return OPTIONS.stream()
                .filter(option -> event.isChar(option.key()))
                .findFirst()
                .map(option -> {
                    choose(option);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private void choose(Option option) {
        // A disabled option is not actionable.
        if (!option.enabled(Boolean.TRUE.equals(remoteAvailable))) {
            return;
        }
        list.selected(0);
        dispatch.accept(option.msg());
    }
}
