package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import java.util.regex.Pattern;

/**
 * What the base branch of a pull request on Forgejo requires before it is merged, as far as jazygit tells it (ADR
 * 181), as {@code GET /repos/{owner}/{repo}/branches/{branch}} gives it.
 *
 * @param guarded           whether a rule of the repository protects the branch
 * @param statusChecks      whether the rule requires checks to pass
 * @param contexts          the checks it requires, as globs; none for all of them, by their combined state
 * @param requiredApprovals how many approvals it requires
 */
public record ForgejoBranchRules(boolean guarded, boolean statusChecks, List<String> contexts, int requiredApprovals) {

    /**
     * No rule: nothing is waited for.
     */
    public static final ForgejoBranchRules NONE = new ForgejoBranchRules(false, false, List.empty(), 0);

    /**
     * Whether a pull request whose head's checks are {@code status}, with {@code approvals} approvals, is not merged
     * yet by these rules: a required check has not passed, or approvals are missing. Forgejo merges it once they are
     * met when it is merged when its checks succeed; else it refuses it.
     */
    public boolean waitFor(CommitStatus status, int approvals) {
        return guarded && ((statusChecks && !passed(status)) || approvals < requiredApprovals);
    }

    /**
     * Whether the checks are passed as Forgejo tells it: without required contexts, their combined state succeeded,
     * which none do not; with them, each glob matches a check, and every check it matches passed.
     */
    private boolean passed(CommitStatus status) {
        if (contexts.isEmpty()) {
            return !status.statuses().isEmpty()
                    && status.statuses().forAll(check -> check.state() == CommitStatus.State.PASSED);
        }
        return contexts.map(ForgejoBranchRules::glob).forAll(glob -> {
            List<CommitStatus.Status> matched = status.statuses()
                    .filter(check -> glob.matcher(check.context()).matches());
            return !matched.isEmpty() && matched.forAll(check -> check.state() == CommitStatus.State.PASSED);
        });
    }

    /**
     * {@code pattern} as Forgejo compiles a required context, with gobwas' glob and no separators: {@code *} any
     * text, {@code ?} any character, {@code [...]} one of them, {@code {a,b}} either; anything else as it is.
     */
    static Pattern glob(String pattern) {
        return Pattern.compile(
                List.ofAll(pattern.toCharArray())
                        .foldLeft(new Glob("", 0, false), Glob::next)
                        .regex(),
                Pattern.DOTALL);
    }

    /**
     * A glob turned into a regular expression up to a character: the expression so far, how many braces are open,
     * and whether it is in a class of characters, {@code [...]}.
     */
    private record Glob(String regex, int braces, boolean inClass) {

        Glob next(char c) {
            if (inClass) {
                // A class that starts with ! is one of the characters it does not have.
                String added = c == '!' && regex.endsWith("[") ? "^" : String.valueOf(c);
                return new Glob(regex + added, braces, c != ']');
            }
            return switch (c) {
                case '*' -> new Glob(regex + ".*", braces, false);
                case '?' -> new Glob(regex + ".", braces, false);
                case '[' -> new Glob(regex + "[", braces, true);
                case '{' -> new Glob(regex + "(?:", braces + 1, false);
                case '}' ->
                    braces > 0 ? new Glob(regex + ")", braces - 1, false) : new Glob(regex + "\\}", braces, false);
                case ',' -> new Glob(regex + (braces > 0 ? "|" : ","), braces, false);
                default -> new Glob(regex + Pattern.quote(String.valueOf(c)), braces, false);
            };
        }
    }
}
