package net.vanfleteren.jazygit.settings;

import io.vavr.collection.SortedSet;
import io.vavr.collection.TreeSet;

/**
 * The pull requests of a repository that were opened and not seen yet, by their number (ADR 29). Numbers only go
 * up, so a pull request above {@code highest} was opened since the last read.
 *
 * @param highest the highest number read of the repository's open pull requests
 * @param numbers the numbers of the ones that were opened since they were first read, and not seen yet
 */
public record UnseenPullRequests(int highest, SortedSet<Integer> numbers) {

    /**
     * None unseen, with {@code highest} the highest number read.
     */
    public static UnseenPullRequests upTo(int highest) {
        return new UnseenPullRequests(highest, TreeSet.empty());
    }

    /**
     * These, with {@code number} seen.
     */
    public UnseenPullRequests seen(int number) {
        return new UnseenPullRequests(highest, numbers.remove(number));
    }
}
