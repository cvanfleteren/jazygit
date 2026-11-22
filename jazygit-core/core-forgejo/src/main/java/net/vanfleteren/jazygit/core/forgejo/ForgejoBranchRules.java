package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
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
     * which none do not; with them, each glob matches a check, and every check it matches passed. A context that is
     * no glob is skipped, as Forgejo skips one it cannot compile.
     */
    private boolean passed(CommitStatus status) {
        if (contexts.isEmpty()) {
            return !status.statuses().isEmpty()
                    && status.statuses().forAll(check -> check.state() == CommitStatus.State.PASSED);
        }
        return contexts.flatMap(ForgejoBranchRules::glob).forAll(glob -> {
            List<CommitStatus.Status> matched = status.statuses()
                    .filter(check -> glob.matcher(check.context()).matches());
            return !matched.isEmpty() && matched.forAll(check -> check.state() == CommitStatus.State.PASSED);
        });
    }

    /**
     * {@code pattern} as Forgejo compiles a required context, with gobwas' glob and no separators: {@code *} any
     * text, {@code ?} any character, {@code [...]} one of the characters in it, {@code [!...]} one not in it, with
     * {@code a-z} a range, and {@code {a,b}} either; {@code \} takes the character after it as it is, in a class too;
     * anything else as it is. None when it is no glob, as a class or a brace left open, or a {@code \} that ends it.
     */
    static Option<Pattern> glob(String pattern) {
        Glob read = List.ofAll(pattern.toCharArray()).foldLeft(new Glob("", 0, false, false, false), Glob::next);
        return read.braces() > 0 || read.inClass() || read.escaped()
                ? Option.none()
                : Try.of(() -> Pattern.compile(read.regex(), Pattern.DOTALL)).toOption();
    }

    /**
     * A glob turned into a regular expression up to a character: the expression so far, how many braces are open,
     * whether it is in a class of characters, {@code [...]}, whether that class was just opened, and whether the
     * character before was a {@code \}, which takes the next one as it is.
     */
    private record Glob(String regex, int braces, boolean inClass, boolean opened, boolean escaped) {

        Glob next(char c) {
            if (escaped) {
                return new Glob(
                        regex + (inClass ? literalInClass(c) : Pattern.quote(String.valueOf(c))),
                        braces,
                        inClass,
                        false,
                        false);
            }
            if (c == '\\') {
                return new Glob(regex, braces, inClass, false, true);
            }
            if (inClass) {
                return new Glob(regex + inClass(c), braces, c != ']', false, false);
            }
            return switch (c) {
                case '*' -> outside(".*", braces);
                case '?' -> outside(".", braces);
                case '[' -> new Glob(regex + "[", braces, true, true, false);
                case '{' -> outside("(?:", braces + 1);
                case '}' -> braces > 0 ? outside(")", braces - 1) : outside("\\}", braces);
                case ',' -> outside(braces > 0 ? "|" : ",", braces);
                default -> outside(Pattern.quote(String.valueOf(c)), braces);
            };
        }

        private Glob outside(String added, int open) {
            return new Glob(regex + added, open, false, false, false);
        }

        // A class that starts with ! is one of the characters it does not have; - makes a range, and ] closes it. Any
        // other character is only itself, as [, & or ^ are not in Java's classes.
        private String inClass(char c) {
            if (c == '!' && opened) {
                return "^";
            }
            return c == '-' || c == ']' ? String.valueOf(c) : literalInClass(c);
        }

        private static String literalInClass(char c) {
            return Character.isLetterOrDigit(c) ? String.valueOf(c) : "\\" + c;
        }
    }
}
