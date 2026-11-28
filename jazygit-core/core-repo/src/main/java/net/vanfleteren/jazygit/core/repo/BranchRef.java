package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A branch whose log can be shown: a local one, or a remote-tracking one. It is the one place a branch's ref is
 * spelled.
 */
public sealed interface BranchRef extends Revision {

    /**
     * Where the refs of the local branches are.
     */
    String HEADS = "refs/heads/";

    /**
     * Where the refs of the remote-tracking branches are.
     */
    String REMOTES = "refs/remotes/";

    /**
     * The name git shows for it, e.g. {@code main} or {@code origin/main}.
     */
    String name();

    /**
     * Its ref, e.g. {@code refs/heads/main} or {@code refs/remotes/origin/main}.
     */
    @Override
    String toArg();

    @Override
    default String label() {
        return name();
    }

    /**
     * What {@code branch} is compared with, the first of them that exists counting (ADR 20): the branch it was made
     * from, {@code parent}, else the main branch, {@code main}; of each the remote-tracking one on the base remote
     * before the local one. Never the branch itself: the main branch has none. Both the commits ahead of the base in
     * the log and those behind it in the branches list (ADR 46) count against these.
     *
     * @param branch the name of the branch, none for a detached HEAD
     * @param onRemote the base remote, none when there is none: then only the local ones count
     */
    static List<BranchRef> bases(
            Option<BranchName> branch,
            Option<BranchName> parent,
            Option<BranchName> main,
            Option<RemoteName> onRemote) {
        return parent.toList()
                .appendAll(main)
                .filter(base -> !branch.contains(base))
                .distinct()
                .flatMap(name -> onRemote.<BranchRef>map(remote -> new Remote(new RemoteBranch(remote, name)))
                        .toList()
                        .append(new Local(name)));
    }

    /**
     * The local branch {@code branch}.
     */
    record Local(BranchName branch) implements BranchRef {

        @Override
        public String name() {
            return branch.value();
        }

        @Override
        public String toArg() {
            return HEADS + branch.value();
        }
    }

    /**
     * The remote-tracking branch of {@code branch}.
     */
    record Remote(RemoteBranch branch) implements BranchRef {

        @Override
        public String name() {
            return branch.shortName();
        }

        @Override
        public String toArg() {
            return REMOTES + branch.shortName();
        }
    }
}
