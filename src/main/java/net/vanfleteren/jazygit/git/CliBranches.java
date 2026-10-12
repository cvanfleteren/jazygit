package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Reads the branches through {@code git for-each-ref}, in one call for all of them.
 */
public final class CliBranches {

    static final String DEFAULT_REMOTE = "origin";
    private static final String SEPARATOR = "\u001f";
    private static final String HEADS = "refs/heads/";
    private static final String REMOTES = "refs/remotes/";
    private static final String ORIGIN = REMOTES + DEFAULT_REMOTE + "/";
    private static final String FORMAT = List.of("%(refname)", "%(objectname)", "%(committerdate:unix)", "%(HEAD)",
            "%(upstream:short)", "%(upstream:track)", "%(upstream:remotename)", "%(symref)").mkString("%1f");
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d+)");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d+)");
    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current).reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(Branch::name);

    private CliBranches() {
    }

    /**
     * The local branches: the checked out one first, then the most recently committed to.
     */
    public static Either<ProcessError, List<Branch>> list(Path workTree) {
        return GitProcess.run(workTree, git("--no-optional-locks", "for-each-ref", "--format=" + FORMAT, HEADS, REMOTES))
                .map(output -> parse(output.stdout()))
                .mapLeft(GitFailure::error);
    }

    /**
     * One line of {@code for-each-ref} output.
     *
     * @param upstream   the short name of the upstream, e.g. {@code origin/main}; empty without one
     * @param track      how far the branch is from its upstream, e.g. {@code [ahead 1, behind 2]}; empty when
     *                   they are equal, {@code [gone]} when the upstream does not exist
     * @param remoteName the remote of the upstream; empty without one
     * @param symref     what a symbolic ref points to, e.g. {@code refs/remotes/origin/main}
     */
    private record Ref(String name, Sha tip, Instant time, boolean head, String upstream, String track,
                       String remoteName, String symref) {

        static Ref parse(String line) {
            String[] f = line.split(SEPARATOR, -1);
            return new Ref(f[0], new Sha(f[1]), Instant.ofEpochSecond(Long.parseLong(f[2])), f[3].equals("*"),
                    f[4], f[5], f[6], f[7]);
        }

        String shortName() {
            return name.substring(HEADS.length());
        }

        /**
         * The remote the branch is pushed to: the one of its upstream, or {@code origin}.
         */
        String remote() {
            return remoteName.isEmpty() ? DEFAULT_REMOTE : remoteName;
        }
    }

    static List<Branch> parse(String raw) {
        List<Ref> refs = List.ofAll(raw.lines()).filter(line -> !line.isBlank()).map(Ref::parse);
        List<Ref> local = refs.filter(ref -> ref.name().startsWith(HEADS));
        Set<String> remote = refs.map(Ref::name).filter(name -> name.startsWith(REMOTES)).toSet();
        Option<String> defaultBranch = defaultBranch(refs, local.map(Ref::shortName));
        return local
                .map(ref -> new Branch(ref.shortName(), ref.head(), ref.tip(), ref.time(),
                        remote.contains(REMOTES + ref.remote() + "/" + ref.shortName()),
                        defaultBranch.contains(ref.shortName()),
                        upstream(ref)))
                .sorted(ORDER);
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that is among {@code branches}.
     */
    private static Option<String> defaultBranch(List<Ref> refs, List<String> branches) {
        return refs.find(ref -> ref.name().equals(ORIGIN + "HEAD"))
                .map(Ref::symref)
                .filter(target -> target.startsWith(ORIGIN))
                .map(target -> target.substring(ORIGIN.length()))
                .orElse(() -> List.of("main", "master").find(branches::contains));
    }

    private static Option<Upstream> upstream(Ref ref) {
        return ref.upstream().isEmpty() || ref.track().equals("[gone]")
                ? Option.none()
                : Option.of(new Upstream(ref.upstream(), count(AHEAD, ref.track()), count(BEHIND, ref.track())));
    }

    private static int count(Pattern pattern, String track) {
        Matcher matcher = pattern.matcher(track);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
