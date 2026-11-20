package net.vanfleteren.jazygit.kernel.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * The keys a store reads and writes: each at a path of its own, which is in no other's.
 */
class SettingKeysTest {

    private static final SettingKey<Boolean> UI = SettingKey.of("ui", true, Codec.bool());
    private static final SettingKey<Boolean> ANIMATIONS = SettingKey.of("ui.animations", true, Codec.bool());
    private static final SettingKey<Boolean> AGAIN = SettingKey.of("ui.animations", false, Codec.bool());
    private static final SettingKey<Boolean> UIX = SettingKey.of("uix", true, Codec.bool());

    @Test
    void theKernelsKeysDoNotClash() {
        assertEquals(List.empty(), SettingKeys.KERNEL.clashes());
    }

    @Test
    void twoKeysAtOnePathClash() {
        assertEquals(List.of("ui.animations"), new SettingKeys(List.of(ANIMATIONS, AGAIN)).clashes());
    }

    @Test
    void aKeyInsideAnothersPathClashes() {
        assertEquals(List.of("ui.animations"), new SettingKeys(List.of(UI, ANIMATIONS)).clashes());
    }

    @Test
    void aPathThatOnlyStartsAsAnotherDoesNotClash() {
        assertEquals(List.empty(), new SettingKeys(List.of(UI, UIX)).clashes());
    }
}
