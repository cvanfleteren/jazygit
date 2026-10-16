package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * How an operation the repository is in the middle of is named.
 */
public final class InProgressText {

    private InProgressText() {}

    /**
     * What kind of operation it is, e.g. "rebase".
     */
    public static String kind(InProgress inProgress) {
        return Messages.get(
                switch (inProgress) {
                    case InProgress.Merging m -> "inProgress.merge";
                    case InProgress.Rebasing r -> "inProgress.rebase";
                    case InProgress.CherryPicking c -> "inProgress.cherryPick";
                    case InProgress.Reverting r -> "inProgress.revert";
                });
    }
}
