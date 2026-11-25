package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.files.Index;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads, for the resolve dialog of the file at {@code path}, what it can be handed to and where its first conflict
 * starts (ADR 201).
 */
public record ReadToolsCmd(String path) implements Cmd {

    @Override
    public Msg run(Env env) {
        Index index = env.git().index();
        return new ResolveMsg.ToolsRead(
                path,
                index.tools().getOrElse(ConflictTools.NONE),
                index.markers(List.of(path))
                        .toOption()
                        .flatMap(List::headOption)
                        .flatMap(file -> file.lines().headOption()));
    }

    /**
     * What could not be read offers nothing more than the dialog does without it.
     */
    @Override
    public Msg failed(Failure failure) {
        return new ResolveMsg.ToolsRead(path, ConflictTools.NONE, Option.none());
    }
}
