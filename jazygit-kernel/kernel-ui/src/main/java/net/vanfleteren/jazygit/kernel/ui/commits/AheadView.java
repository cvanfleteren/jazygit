package net.vanfleteren.jazygit.kernel.ui.commits;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.model.Ahead;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * How a log shows which of its commits are not on the base of its branch: the others dimmed, and the count in the
 * title.
 *
 * @param ahead    the commits not on the base, if the branch has one and they were read
 * @param distance how far the branch is from its base, if that was read (ADR 46)
 */
public record AheadView(Option<Ahead> ahead, Option<BaseDistance> distance) {

    /**
     * {@code title}, followed by how many commits are not on the base and how many of the base are not on the branch,
     * leaving out a count of none, and which branch that is. Until how far the branch is from that base is read, the
     * count of the commits read stands in for those ahead.
     */
    public String title(String title) {
        return ahead.map(read -> distance.filter(counts -> counts.base().equals(read.base())
                                && counts.tip().equals(read.tip()))
                        .map(counts -> title(title, counts.ahead(), false, counts.behind(), read.base()))
                        .getOrElse(() -> title(title, read.commits().size(), read.more(), 0, read.base())))
                .getOrElse(title);
    }

    /**
     * @param more whether there are more than {@code ahead} commits ahead
     */
    private static String title(String title, int ahead, boolean more, int behind, BranchRef base) {
        String aheadText =
                more ? Messages.get("panel.ahead.more", String.valueOf(History.MAX_COMMITS)) : String.valueOf(ahead);
        if (behind == 0) {
            return Messages.get("panel.ahead", title, aheadText, base.name());
        }
        return ahead == 0
                ? Messages.get("panel.behind", title, String.valueOf(behind), base.name())
                : Messages.get("panel.aheadBehind", title, aheadText, String.valueOf(behind), base.name());
    }

    /**
     * Whether {@code sha} is on the base, and so is dimmed.
     */
    public boolean dimmed(Sha sha) {
        return ahead.exists(read -> read.shared(sha));
    }
}
