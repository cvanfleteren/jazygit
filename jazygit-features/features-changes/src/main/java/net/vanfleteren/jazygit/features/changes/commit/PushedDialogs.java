package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The warning the dialogs of rewriting a commit show when that commit is pushed already (#722).
 */
public final class PushedDialogs {

    private PushedDialogs() {}

    /**
     * {@code message}, followed by the warning, if any, after a blank line.
     */
    public static String withWarning(String message, Option<PushedWarning> warning) {
        return warning.map(pushed -> message + "\n\n" + warning(pushed)).getOrElse(message);
    }

    /**
     * The warning, asking whether to go on all the same: for a rewrite that has no confirmation of its own, which the
     * warning comes before.
     */
    public static String goOn(PushedWarning warning) {
        return warning(warning) + "\n\n" + Messages.get("changes.dialog.pushed.goOn");
    }

    /**
     * That the commit is on its upstream already, and, more strongly, when it is on the main branch too.
     */
    public static String warning(PushedWarning warning) {
        String upstream = warning.upstream().shortName();
        return warning.main()
                .map(main -> Messages.get("changes.dialog.pushed.main", upstream, main.value()))
                .getOrElse(() -> Messages.get("changes.dialog.pushed.upstream", upstream));
    }
}
