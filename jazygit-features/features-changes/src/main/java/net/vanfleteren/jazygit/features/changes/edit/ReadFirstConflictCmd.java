package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the file in conflict at {@code path} for where its first conflict starts, to open it there (ADR 201).
 */
public record ReadFirstConflictCmd(String path) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .index()
                .markers(List.of(path))
                .fold(error -> failed(Failure.of(error)), marked -> new EditMsg.FirstConflictRead(path, first(marked)));
    }

    private static Option<Integer> first(List<MarkedFile> marked) {
        return marked.headOption().flatMap(file -> file.lines().headOption());
    }

    /**
     * A file that cannot be read opens at its start, as any other.
     */
    @Override
    public Msg failed(Failure failure) {
        return new EditMsg.FirstConflictRead(path, Option.none());
    }
}
