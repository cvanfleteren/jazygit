package net.vanfleteren.jazygit.git.model;

/**
 * The message of a commit.
 *
 * @param summary     the first line
 * @param description the extended message; may be blank
 */
public record CommitMessage(String summary, String description) {

    /**
     * The message {@code text} is, as git stores it: the first line is the summary, and what follows the blank lines
     * after it is the description.
     */
    public static CommitMessage parse(String text) {
        String stripped = text.strip();
        int end = stripped.indexOf('\n');
        return end < 0
                ? new CommitMessage(stripped, "")
                : new CommitMessage(
                        stripped.substring(0, end).strip(),
                        stripped.substring(end + 1).strip());
    }

    /**
     * The message without the whitespace around its summary and description.
     */
    public CommitMessage stripped() {
        return new CommitMessage(summary.strip(), description.strip());
    }

    public boolean hasSummary() {
        return !summary.isBlank();
    }

    public boolean hasDescription() {
        return !description.isBlank();
    }

    /**
     * The message as git stores it: the summary, and the description after a blank line.
     */
    public String text() {
        return hasDescription() ? summary + "\n\n" + description + "\n" : summary + "\n";
    }
}
