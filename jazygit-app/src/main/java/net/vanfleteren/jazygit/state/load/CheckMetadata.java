package net.vanfleteren.jazygit.state.load;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Looks at the files that the branches, the stashes and the worktrees are read from, so that a tick only reads
 * again those that changed: that takes a git process each, this none.
 */
public record CheckMetadata() implements Load {

    /**
     * The loads that a stamp of {@link GitMetadata} tells to run again.
     */
    public static final List<Load> GUARDED = List.of(LoadBranches.LISTED, new LoadStashes(), new LoadWorktrees());

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.metadata"));
    }

    @Override
    public Msg run(Env env) {
        Option<GitMetadata> metadata = env.git().status().metadata();
        return new LoadMsg.MetadataChecked(
                List.of(
                        Tuple.of((Load) LoadBranches.LISTED, metadata.flatMap(GitMetadata::branches)),
                        Tuple.of(new LoadStashes(), metadata.flatMap(GitMetadata::stashes)),
                        Tuple.of(new LoadWorktrees(), metadata.flatMap(GitMetadata::worktrees))),
                metadata.flatMap(GitMetadata::index));
    }
}
