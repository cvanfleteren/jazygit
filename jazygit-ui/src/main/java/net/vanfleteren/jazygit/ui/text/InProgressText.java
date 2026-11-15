package net.vanfleteren.jazygit.ui.text;

import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.state.AppError;

/**
 * How an operation the repository is in the middle of is named.
 */
public final class InProgressText {

    private InProgressText() {}

    /**
     * What kind of operation it is, e.g. "rebase".
     */
    public static String kind(InProgress inProgress) {
        return ErrorText.of(AppError.Unfinished.kind(inProgress));
    }
}
