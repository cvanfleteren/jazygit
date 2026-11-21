package net.vanfleteren.jazygit.kernel.state.diff;

/**
 * Which file of a diff a line is numbered in: the old one or the new one (ADR 171).
 */
public enum DiffSide {
    /**
     * The old file, which a removed line is of.
     */
    OLD,
    /**
     * The new file, which an added or a context line is of.
     */
    NEW;

    /**
     * The side of a diff GitHub's {@code side} is. Temporary, until remarks are the kernel's (ADR 171).
     */
    public static DiffSide of(net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side side) {
        return side == net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.LEFT ? OLD : NEW;
    }

    /**
     * GitHub's side of a diff that {@code side} is. Temporary, until remarks are the kernel's (ADR 171).
     */
    public static net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side gitHub(DiffSide side) {
        return side == OLD
                ? net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.LEFT
                : net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT;
    }
}
