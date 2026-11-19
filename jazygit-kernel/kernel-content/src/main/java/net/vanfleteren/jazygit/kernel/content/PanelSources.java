package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;

/**
 * What the content panel shows of its own, asked after the sources of the features (ADR 154). Between them, they
 * answer for every pane the content panel mirrors.
 */
public final class PanelSources {

    /**
     * The content panel's own sources, in the order they are asked: of each pane, the one that shows what is open in
     * it first, and the one that shows it otherwise last.
     */
    public static final List<ContentSource> ALL = List.of(
            new PullRequestSource(),
            new CommitFilesSource(),
            new UnchangedSource(),
            new FileDiffsSource(),
            new DivergenceSource(),
            new BranchCommitsSource(),
            new BranchLogSource(),
            new CommitSource(),
            new StashSource());

    private PanelSources() {}
}
