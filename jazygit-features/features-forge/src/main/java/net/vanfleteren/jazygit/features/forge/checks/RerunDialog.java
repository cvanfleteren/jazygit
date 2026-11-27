package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * Asks to run the failed jobs of the failed runs on the tip again, naming each, and what is not run again, and why
 * (ADR 135). Enter and Escape both go back to the checks. Every forge shows it alike, so each may list it among its
 * popups: whichever is first shows it.
 */
public final class RerunDialog {

    public static final ConfirmDialog.Spec<RerunPopup> SPEC = new ConfirmDialog.Spec<>(
            "checks-rerun",
            RerunPopup.class,
            popup -> Messages.get("forge.dialog.rerun.title"),
            popup -> message(popup.plan(), popup.checks().port().forge()),
            ChecksMsg.RerunConfirmed::new,
            ChecksMsg.RerunCancelled::new,
            "dialog.hint.confirmCancel");

    // The runs, and the checks not run again, stand in under their heading.
    private static final String INDENT = "  ";

    private RerunDialog() {}

    /**
     * The runs run again, a line each, then those not, under a heading of their own when there are; {@code forge}
     * names the forge whose Actions they are not of.
     */
    static String message(RerunPlan plan, String forge) {
        List<String> runs = List.of(Messages.get("forge.dialog.rerun.runs"))
                .appendAll(plan.runs()
                        .map(run -> INDENT
                                + Messages.get(
                                        "forge.dialog.rerun.run",
                                        run.workflow(),
                                        run.event().isEmpty() ? "#" + run.run() : run.event(),
                                        run.failed().size())));
        List<String> skipped = plan.skipped().isEmpty()
                ? List.empty()
                : List.of("", Messages.get("forge.dialog.rerun.skipped"))
                        .appendAll(plan.skipped().map(skip -> skipped(skip, forge)));
        return runs.appendAll(skipped).mkString("\n");
    }

    private static String skipped(RerunPlan.Skipped skip, String forge) {
        return INDENT
                + switch (skip.why()) {
                    case RUNNING -> Messages.get("forge.dialog.rerun.skippedRunning", skip.name());
                    case NOT_ACTIONS, NONE_FAILED ->
                        Messages.get("forge.dialog.rerun.skippedNotActions", skip.name(), forge);
                };
    }
}
