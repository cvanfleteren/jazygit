package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * How many lines a diff shows around a change, as {@code git diff -U<n>} reads it (ADR 75).
 *
 * @param lines the lines before and after each change; {@link #WHOLE_FILE} shows the whole file
 */
public record DiffContext(int lines) {

    /**
     * Git's own three lines.
     */
    public static final DiffContext DEFAULT = new DiffContext(3);

    /**
     * A context larger than any file jazygit reads, so git shows each changed file as one hunk.
     */
    public static final DiffContext WHOLE_FILE = new DiffContext(1_000_000);

    /**
     * The amounts the user steps along.
     */
    private static final List<DiffContext> STEPS =
            List.of(0, 1, 3, 5, 10, 20, 50).map(DiffContext::new).append(WHOLE_FILE);

    /**
     * Whether this shows the whole file.
     */
    public boolean wholeFile() {
        return lines >= WHOLE_FILE.lines;
    }

    /**
     * The next step with more context, or this when there is none.
     */
    public DiffContext wider() {
        return STEPS.find(step -> step.lines > lines).getOrElse(this);
    }

    /**
     * The next step with less context, or this when there is none.
     */
    public DiffContext narrower() {
        return STEPS.reverse().find(step -> step.lines < lines).getOrElse(this);
    }

    /**
     * The flag of {@code git diff} and {@code git show} that reads this context.
     */
    public String flag() {
        return "-U" + lines;
    }
}
