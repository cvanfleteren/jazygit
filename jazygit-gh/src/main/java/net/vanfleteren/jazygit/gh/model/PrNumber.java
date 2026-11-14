package net.vanfleteren.jazygit.gh.model;

/**
 * The number of a pull request in its repository, e.g. 42 for {@code #42}. Ordered by number, so by when they were
 * opened.
 */
public record PrNumber(int value) implements Comparable<PrNumber> {

    @Override
    public int compareTo(PrNumber other) {
        return Integer.compare(value, other.value);
    }
}
