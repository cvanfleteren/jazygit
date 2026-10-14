package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

import java.time.Instant;

/**
 * A git branch, with a flag indicating whether it is the currently checked-out branch.
 *
 * @param tipOid  the commit the branch points to
 * @param tipTime when that commit was made
 * @param hasRemote whether the branch also exists on its remote, according to the remote-tracking refs
 * @param isDefault whether this is the repository's main branch, i.e. the default branch of the remote
 * @param tracking  what it follows on its remote: nothing, a remote-tracking branch, or one that is gone
 */
public record Branch(Name name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote,
                     boolean isDefault, Tracking tracking) {

    /**
     * The name of a local branch, e.g. {@code main} or {@code feature/login}.
     */
    public record Name(String value) {

        /**
         * The name {@code text} spells, ignoring surrounding whitespace; none when it is blank or has whitespace
         * inside, which no branch name can.
         */
        public static Option<Name> parse(String text) {
            String stripped = text.strip();
            return stripped.isEmpty() || stripped.chars().anyMatch(Character::isWhitespace)
                    ? Option.none()
                    : Option.of(new Name(stripped));
        }
    }

    /**
     * A branch following {@code upstream}, or nothing.
     */
    public Branch(Name name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote,
                  boolean isDefault, Option<Upstream> upstream) {
        this(name, current, tipOid, tipTime, hasRemote, isDefault,
                upstream.<Tracking>map(Tracking.Tracks::new).getOrElse(new Tracking.None()));
    }

    public Branch(Name name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote,
                  boolean isDefault) {
        this(name, current, tipOid, tipTime, hasRemote, isDefault, new Tracking.None());
    }

    /**
     * The remote-tracking branch it follows, if that exists; none when its upstream is gone.
     */
    public Option<Upstream> upstream() {
        return tracking instanceof Tracking.Tracks(Upstream upstream) ? Option.of(upstream) : Option.none();
    }

    /**
     * Its upstream, if that is a branch on a remote, which it can be pushed to; none when it follows a local
     * branch.
     */
    public Option<Upstream> remoteUpstream() {
        return upstream().filter(upstream -> !upstream.branch().local());
    }

    /**
     * The remote branch its upstream was, when that was deleted on the remote.
     */
    public Option<RemoteBranch> goneUpstream() {
        return tracking instanceof Tracking.Gone(RemoteBranch upstream) ? Option.of(upstream) : Option.none();
    }

    /**
     * The remote branch it is pushed to: its upstream, even when that is gone, or the branch of the same name
     * on {@code origin} when it has none, or follows a local branch.
     */
    public RemoteBranch pushTarget() {
        return upstream().map(Upstream::branch)
                .orElse(this::goneUpstream)
                .filter(upstream -> !upstream.local())
                .getOrElse(() -> new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name));
    }

    /**
     * The commits not yet pushed to the upstream branch; 0 without an upstream.
     */
    public int ahead() {
        return upstream().map(Upstream::ahead).getOrElse(0);
    }

    /**
     * The commits on the upstream branch not yet in this branch; 0 without an upstream.
     */
    public int behind() {
        return upstream().map(Upstream::behind).getOrElse(0);
    }

    public Branch(Name name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote) {
        this(name, current, tipOid, tipTime, hasRemote, false);
    }

    /**
     * Whether the branch can be deleted on its remote: it must be there, and it must not be the
     * default branch, which would break every clone.
     */
    public boolean remoteDeletable() {
        return hasRemote && !isDefault;
    }

    public Branch(Name name, boolean current, Sha tipOid, Instant tipTime) {
        this(name, current, tipOid, tipTime, false, false);
    }

    public Branch(Name name, boolean current, Sha tipOid) {
        this(name, current, tipOid, Instant.EPOCH, false, false);
    }
}
