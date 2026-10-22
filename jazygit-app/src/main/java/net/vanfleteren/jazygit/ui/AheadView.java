package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * How a log shows which of its commits are not on the base of its branch: the others dimmed, and the count in the
 * title; or only those, with the commit they forked from.
 *
 * @param ahead the commits not on the base, if the branch has one and they were read
 * @param only  whether only those are listed
 */
record AheadView(Option<Ahead> ahead, boolean only) {

    /**
     * {@code title}, followed by how many commits are not on the base, and which branch that is.
     */
    String title(String title) {
        return ahead.map(read -> Messages.get(
                        only ? "panel.ahead.only" : "panel.ahead",
                        title,
                        read.more()
                                ? Messages.get("panel.ahead.more", GitInfoProvider.MAX_COMMITS)
                                : String.valueOf(read.commits().size()),
                        read.base().name()))
                .getOrElse(title);
    }

    /**
     * The commits to list of {@code log}: all of them, or only those not on the base and the fork point.
     */
    List<Commit> shown(List<Commit> log) {
        return ahead.filter(read -> only)
                .map(read -> read.commits().appendAll(forkPoint(read)))
                .getOrElse(log);
    }

    /**
     * Whether {@code sha} is on the base, and so is dimmed.
     */
    boolean dimmed(Sha sha) {
        return ahead.exists(read -> read.shared(sha));
    }

    /**
     * What marks {@code sha} as the commit the branch forked from, when it is listed as that.
     */
    Option<String> mark(Sha sha) {
        return ahead.filter(read ->
                        only && forkPoint(read).exists(fork -> fork.sha().equals(sha)))
                .map(read -> Messages.get("panel.forkPoint", read.base().name()));
    }

    /**
     * The fork point, unless there are more commits than are listed: then it is not where they start.
     */
    private static Option<Commit> forkPoint(Ahead ahead) {
        return ahead.more() ? Option.none() : ahead.forkPoint();
    }
}
