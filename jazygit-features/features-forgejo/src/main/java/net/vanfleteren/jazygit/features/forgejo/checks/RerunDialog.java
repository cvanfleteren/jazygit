package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * Asks to run the failed jobs of the failed runs on the tip again, naming each, and what is not run again, and why
 * (ADR 135, 195). Enter and Escape both go back to the checks.
 */
public final class RerunDialog {

    public static final ConfirmDialog.Spec<RerunPopup> SPEC = new ConfirmDialog.Spec<>(
            "forgejo-checks-rerun",
            RerunPopup.class,
            popup -> Messages.get("forgejo.dialog.rerun.title"),
            popup -> message(popup.plan()),
            ChecksMsg.RerunConfirmed::new,
            ChecksMsg.RerunCancelled::new,
            "dialog.hint.confirmCancel");

    // The runs, and the checks not run again, stand in under their heading.
    private static final String INDENT = "  ";

    private RerunDialog() {}

    /**
     * The runs run again, a line each, then those not, under a heading of their own when there are.
     */
    static String message(RerunPlan plan) {
        List<String> runs = List.of(Messages.get("forgejo.dialog.rerun.runs"))
                .appendAll(plan.runs()
                        .map(run -> INDENT
                                + Messages.get(
                                        "forgejo.dialog.rerun.run",
                                        run.workflow(),
                                        run.event().isEmpty() ? "#" + run.run() : run.event(),
                                        run.failed().size())));
        List<String> skipped = plan.skipped().isEmpty()
                ? List.empty()
                : List.of("", Messages.get("forgejo.dialog.rerun.skipped"))
                        .appendAll(plan.skipped()
                                .map(skip -> INDENT
                                        + Messages.get(
                                                switch (skip.why()) {
                                                    case RUNNING -> "forgejo.dialog.rerun.skippedRunning";
                                                    case NOT_ACTIONS, NONE_FAILED ->
                                                        "forgejo.dialog.rerun.skippedNotActions";
                                                },
                                                skip.name())));
        return runs.appendAll(skipped).mkString("\n");
    }
}
