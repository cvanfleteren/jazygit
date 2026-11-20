package net.vanfleteren.jazygit.features.chrome.settings;

import io.vavr.collection.List;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * The IO of the settings panel: the settings file.
 */
public sealed interface SettingsPanelCmd extends Cmd {

    /**
     * Reads the settings file again, as the panel opens, for what was edited outside jazygit (ADR 111). On the
     * {@code io} executor, as saving runs there: a save asked for before is written before this reads.
     */
    record ReadFile() implements SettingsPanelCmd {

        @Override
        public Msg run(Env env) {
            return new SettingsPanelMsg.FileRead(
                    env.settings().file().map(Path::toString), env.settings().load());
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.FileNotRead(failure);
        }
    }

    /**
     * Opens the settings file in git's editor, with the terminal handed over to it, made empty first when it is not
     * there, and reads it again once the editor is closed (ADR 111). On the {@code io} executor, as saving runs there.
     */
    record EditFile() implements SettingsPanelCmd {

        public static final Operation EDIT_SETTINGS =
                Operation.of("chrome.log.title.editSettings", "chrome.error.editSettings.failed");

        @Override
        public Msg run(Env env) {
            return env.settings()
                    .made()
                    .<Failure>mapLeft(Failure.Settings::new)
                    .flatMap(file -> env.terminal()
                            .handOver(() -> env.git().editor().edit(List.of(file.toString())))
                            .mapLeft(Failure::of))
                    .fold(
                            this::failed,
                            commands -> new SettingsPanelMsg.FileEdited(
                                    commands, env.settings().load()));
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.FileNotEdited(failure);
        }
    }
}
