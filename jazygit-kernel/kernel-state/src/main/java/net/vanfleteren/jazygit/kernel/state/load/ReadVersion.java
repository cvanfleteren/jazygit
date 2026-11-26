package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Blames;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * Reads the diff of the file with the path {@code path} again, the way {@code source} says, with the whole file as
 * context, to show a version of it in the area {@code area} while it shows {@code content} (ADR 102). It reads with
 * {@code options}, whose whitespace is never ignored: git prints a line whose whitespace only changed as it is in the
 * new file, and the original would have it so. So it is no {@link DiffLoad}: what it reads is not dropped when the
 * options change.
 */
public record ReadVersion(
        AreaId area, ScrolledContent content, WholeFile.Source source, RepoPath path, DiffOptions options)
        implements Targeted<DiffText> {

    /**
     * The read of the file with the path {@code path} as {@code source} says, with {@code options} but whitespace
     * not ignored.
     */
    public static ReadVersion of(
            AreaId area, ScrolledContent content, WholeFile.Source source, RepoPath path, DiffOptions options) {
        return new ReadVersion(area, content, source, path, options.withIgnoreWhitespace(false));
    }

    /**
     * The read of {@code file} for the area {@code area}, as the version keeps it.
     */
    public static ReadVersion of(AreaId area, WholeFile file) {
        return new ReadVersion(area, file.content(), file.source(), file.path(), file.options());
    }

    /**
     * What this reads, as the version keeps it.
     */
    public WholeFile file() {
        return new WholeFile(content, source, path, options);
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.wholeFile", path));
    }

    /**
     * The file read whole of the version of the area, while it is read this way. A file read again that changed since
     * drops the blames read of it (ADR 147), whose lines are no longer its lines.
     */
    @Override
    public Target<DiffText> target() {
        return Target.inView(new Lens<RepoView, Loadable<DiffText>>(
                view -> version(view).map(Version::whole),
                (view, change) -> version(view)
                        .map(version -> {
                            Loadable<DiffText> whole = change.apply(version.whole());
                            boolean changed = whole.toOption().isDefined()
                                    && !whole.toOption().equals(version.whole().toOption());
                            return view.withVersion(
                                    area,
                                    version.withWhole(whole)
                                            .withBlames(
                                                    changed
                                                            ? Blames.none(version.blames()
                                                                    .ignoreWhitespace())
                                                            : version.blames()));
                        })
                        .getOrElse(view)));
    }

    private Option<Version> version(RepoView view) {
        return view.versionOf(area).filter(version -> version.read().equals(file()));
    }

    @Override
    public Msg run(Env env) {
        return ReadWholeFile.read(env, source, options).fold(this::failed, text -> new LoadMsg.VersionRead(this, text));
    }
}
