package net.vanfleteren.jazygit.app.keys;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.vavr.collection.List;
import io.vavr.control.Try;
import java.lang.reflect.Field;

/**
 * The constants the app's classes declare, for the tests that keep their names apart ({@code PartNamesTest},
 * {@code SlotNamesTest}).
 */
final class Constants {

    private Constants() {}

    /**
     * The values of the static fields of type {@code type} in the app's main classes, whichever module declares them:
     * not those of the tests, nor of the tests a module ships as a jar.
     */
    static <T> List<T> of(Class<T> type) {
        return List.ofAll(new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .withImportOption(location -> !location.contains("-tests.jar"))
                        .importPackages("net.vanfleteren.jazygit"))
                .flatMap(owner -> List.ofAll(owner.getFields()))
                .filter(field -> field.getRawType().isEquivalentTo(type)
                        && field.getModifiers().contains(JavaModifier.STATIC))
                .map(field -> value(field, type));
    }

    private static <T> T value(JavaField field, Class<T> type) {
        return Try.of(() -> {
                    Field reflected = field.reflect();
                    reflected.setAccessible(true);
                    return type.cast(reflected.get(null));
                })
                .get();
    }
}
