package net.vanfleteren.jazygit.changes.files.step;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.view.FileStepMsg;
import net.vanfleteren.jazygit.state.view.FileStepMsg.Step;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Moving to the next or the previous file of the files pane from the diff in the content panel, which keeps the focus
 * (ADR 117). The keys are in every diff, rich or plain; the app moves the highlight only when what the content panel
 * shows is the files pane's.
 */
public final class FileStepAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(
                            keys,
                            (keymap, scope) -> keymap.add(
                                    scope,
                                    step("J", "help.diff.nextFile", Step.NEXT),
                                    step("K", "help.diff.previousFile", Step.PREVIOUS))));

    private FileStepAction() {}

    private static Binding<DiffCursorAt> step(String key, String descriptionKey, Step step) {
        return Binding.always(key, descriptionKey, () -> new FileStepMsg(step));
    }
}
