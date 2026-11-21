package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.vavr.collection.List;
import io.vavr.control.Try;
import java.lang.reflect.Field;
import net.vanfleteren.jazygit.kernel.state.Part;
import org.junit.jupiter.api.Test;

/**
 * The model keeps each {@link Part} under its name (ADR 157), so no two parts the app declares may share one: the
 * second would read the value of the first. Every constant of the app's classes that holds a part counts, whether a
 * feature or any other module declares it.
 */
class PartNamesTest {

    private static List<Part<?>> declared() {
        return List.ofAll(new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("net.vanfleteren.jazygit"))
                .flatMap(type -> List.ofAll(type.getFields()))
                .filter(field -> field.getRawType().isEquivalentTo(Part.class)
                        && field.getModifiers().contains(JavaModifier.STATIC))
                .map(PartNamesTest::value);
    }

    private static Part<?> value(JavaField field) {
        return Try.of(() -> {
                    Field reflected = field.reflect();
                    reflected.setAccessible(true);
                    return (Part<?>) reflected.get(null);
                })
                .get();
    }

    @Test
    void theSwitchesOfTheFeaturesAreAPart() {
        assertTrue(declared().exists(part -> part.name().equals("kernel.features")));
    }

    @Test
    void noTwoPartsShareAName() {
        List<String> names = declared().map(Part::name);
        assertEquals(names.distinct(), names, "two parts share a name");
    }
}
