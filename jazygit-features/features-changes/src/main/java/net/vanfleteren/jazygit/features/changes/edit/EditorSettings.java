package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;

/**
 * The editors the user keeps in the settings file (ADR 110, ADR 163): the programs that open files and worktrees.
 */
public final class EditorSettings {

    /**
     * The editors (ADR 110). An entry that is wrong is left out, and reported; the others still apply. Only editors
     * that are no array at all are not valid as a whole.
     */
    public static final SettingKey<Editors> EDITORS =
            SettingKey.userOwned(Editors.KEY, Editors.NONE, Codec.readOnly(value -> {
                Editors.Read read = Editors.read(value);
                return new Decoded<>(Option.some(read.editors()), read.invalid());
            }));

    private EditorSettings() {}
}
