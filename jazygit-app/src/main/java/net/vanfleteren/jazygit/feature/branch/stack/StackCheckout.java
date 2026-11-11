package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.CaughtUpBranch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.github.FromGitHub;

/**
 * Checking out a stack on GitHub (ADR 99): a local branch for each of its open pull requests, made or fast-forwarded
 * to its branch on the remote, as just fetched; then one of them checked out, or none.
 *
 * @param stack   the number of the stack
 * @param lines   its open pull requests, from the bottom up, each with what happens to its branch
 * @param skipped its merged and closed pull requests, by number, which are left out
 * @param on      the number of the pull request it was asked for on, if it was asked for on one
 */
public record StackCheckout(int stack, List<Line> lines, List<Integer> skipped, Option<Integer> on) {

    /**
     * The remote the branches of {@code stack} are fetched from: the one the local branch of an open pull request of
     * it follows, as they are all pushed to one (ADR 74); else {@code origin}.
     */
    public static RemoteName remote(Model model, PullRequestStack stack) {
        return open(stack)
                .flatMap(entry -> StackPush.checkedOut(model, entry))
                .flatMap(Branch::remoteUpstream)
                .headOption()
                .map(RemoteBranch::remote)
                .getOrElse(RemoteBranch.DEFAULT_REMOTE);
    }

    /**
     * The branches of the open pull requests of {@code stack} on the remote that are fetched: all of them but those
     * of a fork.
     */
    public static List<BranchName> fetched(PullRequestStack stack) {
        return open(stack)
                .filter(entry -> !entry.fork() && entry.headBranch().isKnown())
                .map(entry -> FromGitHub.branch(entry.headBranch()));
    }

    /**
     * What the menu says of {@code stack} without reading anything: whether an open pull request has no local branch,
     * else whether each local branch is where GitHub last had its branch. A branch whose commit on GitHub is not known
     * may not be: the stack is then said to need an update.
     */
    public static Status status(Model model, PullRequestStack stack) {
        List<PullRequestStack.Entry> open = open(stack).filter(entry -> !entry.fork());
        if (open.isEmpty()) {
            return Status.NOTHING;
        }
        List<Option<Branch>> local = open.map(entry -> StackPush.checkedOut(model, entry));
        if (local.exists(Option::isEmpty)) {
            return Status.CHECK_OUT;
        }
        boolean atHead = open.zip(local)
                .forAll(pair -> pair._1()
                        .headOid()
                        .flatMap(Sha::parse)
                        .exists(head -> head.equals(pair._2().get().tip())));
        return atHead ? Status.UP_TO_DATE : Status.UPDATE;
    }

    /**
     * What checking out {@code stack} does, its branches on {@code remote} just fetched, and {@code read} the branches
     * as they are after: for each open pull request, from the bottom up, what happens to its branch.
     *
     * @param on the number of the pull request it was asked for on, if it was asked for on one
     */
    public static StackCheckout of(PullRequestStack stack, RemoteName remote, AllBranches read, Option<Integer> on) {
        List<PullRequestStack.Entry> entries = stack.entries().sortBy(PullRequestStack.Entry::position);
        return new StackCheckout(
                stack.number(),
                entries.filter(StackCheckout::isOpen).map(entry -> line(entry, remote, read)),
                entries.filter(entry -> !isOpen(entry)).map(PullRequestStack.Entry::number),
                on);
    }

    private static Line line(PullRequestStack.Entry entry, RemoteName remote, AllBranches read) {
        BranchName name = FromGitHub.branch(entry.headBranch());
        RemoteBranch upstream = new RemoteBranch(remote, name);
        Option<BranchName> parent =
                Option.of(entry.baseBranch()).filter(GhBranch::isKnown).map(FromGitHub::branch);
        if (entry.fork()) {
            return new Line(entry.number(), name, upstream, Kind.FORK, 0, Option.none());
        }
        Option<Branch> following =
                read.local().find(branch -> branch.remoteUpstream().contains(upstream));
        if (following.isDefined()) {
            Branch branch = following.get();
            return new Line(
                    entry.number(),
                    branch.name(),
                    upstream,
                    kind(branch, read, upstream),
                    commits(branch),
                    Option.none());
        }
        return read.local()
                .find(branch -> branch.name().equals(name))
                .map(taken -> new Line(
                        entry.number(),
                        name,
                        upstream,
                        taken.upstream().isEmpty() ? Kind.NO_UPSTREAM : Kind.OTHER_UPSTREAM,
                        0,
                        Option.none()))
                .getOrElse(() -> new Line(entry.number(), name, upstream, Kind.MADE, 0, parent));
    }

    /**
     * What happens to {@code branch}, which follows {@code upstream}.
     */
    private static Kind kind(Branch branch, AllBranches read, RemoteBranch upstream) {
        boolean atRemote = read.remote()
                .find(ref -> ref.branch().equals(upstream))
                .map(RemoteRef::tip)
                .contains(branch.tip());
        if (atRemote || (branch.ahead() == 0 && branch.behind() == 0)) {
            return Kind.UP_TO_DATE;
        }
        if (branch.ahead() > 0) {
            return Kind.OWN_COMMITS;
        }
        if (branch.current()) {
            return Kind.CHECKED_OUT_HERE;
        }
        return branch.checkedOutElsewhere() ? Kind.OTHER_WORKTREE : Kind.FAST_FORWARDED;
    }

    private static int commits(Branch branch) {
        return branch.ahead() > 0 ? branch.ahead() : branch.behind();
    }

    private static List<PullRequestStack.Entry> open(PullRequestStack stack) {
        return stack.entries().filter(StackCheckout::isOpen).sortBy(PullRequestStack.Entry::position);
    }

    private static boolean isOpen(PullRequestStack.Entry entry) {
        return entry.state() == State.OPEN;
    }

    /**
     * Whether it can start: no local branch of the name of a pull request's branch may be another one.
     */
    public boolean startable() {
        return !lines.exists(line -> line.kind().refuses());
    }

    /**
     * The branches that are made or fast-forwarded, from the bottom up.
     */
    public List<CaughtUpBranch> caughtUp() {
        return lines.filter(line -> line.kind().moves())
                .map(line ->
                        new CaughtUpBranch(line.branch(), line.upstream(), line.kind() == Kind.MADE, line.parent()));
    }

    /**
     * The branch of the highest open pull request that has one once done.
     */
    public Option<BranchName> top() {
        return lines.filter(line -> line.kind().landable()).lastOption().map(Line::branch);
    }

    /**
     * The branch of the pull request it was asked for on, if it has one once done.
     */
    public Option<BranchName> asked() {
        return on.flatMap(number -> lines.find(
                        line -> line.number() == number && line.kind().landable()))
                .map(Line::branch);
    }

    /**
     * How many branches {@code kind} happens to.
     */
    public int count(Kind kind) {
        return lines.count(line -> line.kind() == kind);
    }

    /**
     * How many branches are kept as they are, though they are not where their branch on the remote is.
     */
    public int kept() {
        return lines.count(line -> line.kind().kept());
    }

    /**
     * The branch to check out for {@code landing}, if any.
     */
    public Option<BranchName> target(Landing landing) {
        return switch (landing) {
            case TOP -> top();
            case ASKED -> asked();
            case STAY -> Option.none();
        };
    }

    /**
     * Where Enter lands: on the pull request it was asked for on, when it was, else on the top.
     */
    public Landing preferred() {
        return asked().isDefined() ? Landing.ASKED : Landing.TOP;
    }

    /**
     * What happens to the branch of an open pull request of the stack.
     */
    public enum Kind {
        /** It is made, following its branch on the remote. */
        MADE,
        /** It is fast-forwarded to its branch on the remote. */
        FAST_FORWARDED,
        /** It is where its branch on the remote is. */
        UP_TO_DATE,
        /** It is kept as it is: it has commits that are not on its branch on the remote. */
        OWN_COMMITS,
        /** It is kept as it is: it is behind, but checked out here, and is pulled as any checked out branch. */
        CHECKED_OUT_HERE,
        /** It is kept as it is: it is behind, but checked out in another worktree, under whose files it would move. */
        OTHER_WORKTREE,
        /** There is none: the branch of the pull request is in a fork. */
        FORK,
        /** A local branch of its name follows nothing, so it may be another one: the checkout does not start. */
        NO_UPSTREAM,
        /** A local branch of its name follows another branch: the checkout does not start. */
        OTHER_UPSTREAM;

        /**
         * Whether it keeps the checkout from starting.
         */
        public boolean refuses() {
            return this == NO_UPSTREAM || this == OTHER_UPSTREAM;
        }

        /**
         * Whether it is moved: made or fast-forwarded.
         */
        public boolean moves() {
            return this == MADE || this == FAST_FORWARDED;
        }

        /**
         * Whether it is kept as it is although it is not where its branch on the remote is, or has no branch here.
         */
        public boolean kept() {
            return this == OWN_COMMITS || this == CHECKED_OUT_HERE || this == OTHER_WORKTREE || this == FORK;
        }

        /**
         * Whether the pull request has its branch here once done, which can be checked out.
         */
        public boolean landable() {
            return this != FORK && !refuses();
        }
    }

    /**
     * An open pull request of the stack and what happens to its branch.
     *
     * @param number   the pull request
     * @param branch   its local branch: the one following its branch on the remote, or one of that name
     * @param upstream its branch on the remote
     * @param kind     what happens to it
     * @param commits  how many commits it is fast-forwarded by, or has of its own; or is behind, when it is checked out
     *                 somewhere; else 0
     * @param parent   the branch it is built on, recorded for one that is made: the base of its pull request
     */
    public record Line(
            int number, BranchName branch, RemoteBranch upstream, Kind kind, int commits, Option<BranchName> parent) {}

    /**
     * What the branches of a stack are, as the menu says it without reading anything (ADR 99).
     */
    public enum Status {
        /** An open pull request of it has no local branch. */
        CHECK_OUT,
        /** All its open pull requests have a local branch, and one may not be where GitHub has it. */
        UPDATE,
        /** Each open pull request has a local branch where GitHub has its branch. */
        UP_TO_DATE,
        /** None of its open pull requests has its branch in this repository. */
        NOTHING
    }

    /**
     * Where the user lands once the branches are made and fast-forwarded.
     */
    public enum Landing {
        /** On the branch of the highest open pull request. */
        TOP,
        /** On the branch of the pull request it was asked for on. */
        ASKED,
        /** Where the user was: nothing is checked out. */
        STAY
    }
}
