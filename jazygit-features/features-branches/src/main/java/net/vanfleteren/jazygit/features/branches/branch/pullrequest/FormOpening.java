package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

/**
 * How a forge opens its {@link NewPullRequestForm} once the title it starts out with is read: all it needs but the
 * title. A forge gives it as a record of its own, so the command and the message that carry it stay values.
 */
public interface FormOpening {

    /**
     * The popup as it opens, with {@code title} read for the base offered first.
     */
    NewPullRequestForm titled(String title);
}
