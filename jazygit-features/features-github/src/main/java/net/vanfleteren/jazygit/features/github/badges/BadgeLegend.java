package net.vanfleteren.jazygit.features.github.badges;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.github.widgets.PullRequestSymbols;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * What GitHub's badges in the branches pane mean, which its help lists after what git's symbols there mean (ADR 168):
 * the pull request, how its checks and its review are doing, how its merge goes, and where it is in its stack.
 */
public final class BadgeLegend {

    /**
     * The lines, top to bottom.
     */
    public static final List<Legend> BRANCHES = List.of(
            branches("#42", "github.help.branches.legend.pullRequest"),
            branches(
                    texts(
                            PullRequestSymbols.PASSED,
                            PullRequestSymbols.FAILED,
                            PullRequestSymbols.RUNNING,
                            PullRequestSymbols.QUEUED),
                    "github.help.branches.legend.checks"),
            branches(
                    texts(PullRequestSymbols.APPROVED, PullRequestSymbols.CHANGES, PullRequestSymbols.TO_REVIEW),
                    "github.help.branches.legend.review"),
            branches(
                    texts(
                            PullRequestSymbols.MERGING,
                            PullRequestSymbols.ENQUEUED,
                            PullRequestSymbols.MERGE_FAILED,
                            PullRequestSymbols.MERGED),
                    "github.help.branches.legend.merge"),
            branches("2/4", "github.help.branches.legend.stack"));

    private BadgeLegend() {}

    private static Legend branches(String symbols, String descriptionKey) {
        return new Legend(Scopes.BRANCHES.id(), symbols, descriptionKey);
    }

    /**
     * The texts of {@code symbols}, a space apart.
     */
    private static String texts(PullRequestSymbols.Symbol... symbols) {
        return List.of(symbols).map(PullRequestSymbols.Symbol::text).mkString(" ");
    }
}
