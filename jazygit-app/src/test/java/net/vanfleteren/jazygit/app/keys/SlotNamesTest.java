package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.vavr.collection.List;
import io.vavr.control.Try;
import java.lang.reflect.Field;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;
import net.vanfleteren.jazygit.kernel.ui.Slot;
import org.junit.jupiter.api.Test;

/**
 * A {@link Slot} is its name (ADR 202), and what the features give for it is cast to the type it is declared with, so
 * no two slots the app declares may share one: the second would be given what was meant for the first. Every
 * constant of the app's classes that holds a slot counts, whichever module declares it.
 */
class SlotNamesTest {

    private static List<Slot<?>> declared() {
        return List.ofAll(new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        // Nor the tests of the modules that ship their tests as a jar, whose slots are their own.
                        .withImportOption(location -> !location.contains("-tests.jar"))
                        .importPackages("net.vanfleteren.jazygit"))
                .flatMap(type -> List.ofAll(type.getFields()))
                .filter(field -> field.getRawType().isEquivalentTo(Slot.class)
                        && field.getModifiers().contains(JavaModifier.STATIC))
                .map(SlotNamesTest::value);
    }

    private static Slot<?> value(JavaField field) {
        return Try.of(() -> {
                    Field reflected = field.reflect();
                    reflected.setAccessible(true);
                    return (Slot<?>) reflected.get(null);
                })
                .get();
    }

    @Test
    void theKernelsSlotsAreFound() {
        assertTrue(declared().contains(KernelSlots.SPINNING));
    }

    @Test
    void noTwoSlotsShareAName() {
        List<String> names = declared().map(Slot::name);
        assertEquals(names.distinct(), names, "two slots share a name");
    }
}
