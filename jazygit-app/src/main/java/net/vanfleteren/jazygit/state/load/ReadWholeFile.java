package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Lens;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.WholeFile;
import net.vanfleteren.jazygit.state.diff.WholeFile.Source;
import net.vanfleteren.jazygit.state.view.RepoView;

/**
 * Reads the diff of the file with the path {@code path} again, the way {@code source} says, with the whole file
 * as context, to show more of it than its hunks in the area {@code area} while it shows {@code content} (ADR 77).
 * It reads with {@code options} but for the context, so what it read with options changed since is dropped.
 */
public record ReadWholeFile(String area, ScrolledContent content, Source source, String path, DiffOptions options)
        implements DiffLoad, Targeted<DiffText> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.wholeFile", path));
    }

    /**
     * The file read whole of the expansion of the area, while it holds for what was expanded.
     */
    @Override
    public Target<DiffText> target() {
        return Target.inView(new Lens<RepoView, Loadable<DiffText>>(
                view -> expanded(view).map(Expansion.Expanded::whole),
                (view, change) -> expanded(view)
                        .map(file -> view.withExpanded(view.expanded()
                                .computeIfPresent(
                                        area,
                                        (key, expansion) -> expansion.withFiles(expansion
                                                .files()
                                                .put(path, file.withWhole(change.apply(file.whole())))))
                                ._2()))
                        .getOrElse(view)));
    }

    /**
     * The read of {@code file} for the area {@code area}.
     */
    public static ReadWholeFile of(String area, WholeFile file) {
        return new ReadWholeFile(area, file.content(), file.source(), file.path(), file.options());
    }

    /**
     * What this reads, as the expansion keeps it.
     */
    public WholeFile file() {
        return new WholeFile(content, source, path, options);
    }

    private Option<Expansion.Expanded> expanded(RepoView view) {
        return view.expanded()
                .get(area)
                .filter(expansion -> expansion.holdsFor(content))
                .flatMap(expansion -> expansion.files().get(path));
    }

    @Override
    public Msg run(Env env) {
        return read(env, source, options).fold(this::failed, text -> new LoadMsg.WholeFileRead(this, text));
    }

    /**
     * The diff of the file of {@code source}, read the way {@code source} says, with {@code options} but for the
     * context: the whole file is.
     */
    static Either<Failure, DiffText> read(Env env, Source source, DiffOptions options) {
        DiffOptions whole = options.withContext(DiffContext.WHOLE_FILE);
        return (switch (source) {
                    case Source.WorkingTree(FileEntry file, boolean staged) ->
                        env.git()
                                .diffs()
                                .uncommitted(List.of(file), whole)
                                .map(diffs -> staged ? diffs.staged() : diffs.unstaged());
                    case Source.OfCommit(Sha sha, CommitFile file) ->
                        env.git().diffs().commitFileChanges(sha, List.of(file), whole);
                    case Source.OfStash(Sha sha, CommitFile file) ->
                        env.git().stashes().fileChanges(sha, List.of(file), whole);
                    case Source.OfFiles(Changes of, CommitFile file) ->
                        switch (of) {
                            case Changes.OfCommit(Sha sha) ->
                                env.git().diffs().commitFileChanges(sha, List.of(file), whole);
                            case Changes.OfStash(Sha sha) -> env.git().stashes().fileChanges(sha, List.of(file), whole);
                            case Changes.OfBranch(var branch, var base, Sha sha) ->
                                env.git().diffs().rangeFileChanges(base.tip(), sha, List.of(file), whole);
                            case Changes.OfPullRequest(PullRequestCommits commits) ->
                                env.git().diffs().pullRequestFileChanges(commits, List.of(file), whole);
                        };
                })
                .mapLeft(Failure::of);
    }
}
