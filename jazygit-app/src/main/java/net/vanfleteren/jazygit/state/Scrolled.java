package net.vanfleteren.jazygit.state;

/**
 * Where an area of the content panel is scrolled to, and what it showed then: a position only holds for what it
 * was scrolled in, as other content starts at the top.
 *
 * @param content what the area showed
 * @param row     the first line shown
 * @param column  the first column shown
 */
public record Scrolled(ScrolledContent content, int row, int column) {

    /**
     * {@code content} from its start.
     */
    public static Scrolled top(ScrolledContent content) {
        return new Scrolled(content, 0, 0);
    }
}
