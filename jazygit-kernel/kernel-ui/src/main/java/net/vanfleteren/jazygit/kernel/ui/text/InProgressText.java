package net.vanfleteren.jazygit.kernel.ui.text;

import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.kernel.state.AppError;

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
