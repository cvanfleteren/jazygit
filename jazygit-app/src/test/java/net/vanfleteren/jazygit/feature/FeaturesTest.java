package net.vanfleteren.jazygit.feature;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Each feature package declares its {@link Feature} once, and {@link Features#ALL} lists it (ADR 127).
 */
class FeaturesTest {

    private static final String PACKAGE = Feature.class.getPackageName();

    /**
     * The top-level feature packages that are no feature: {@code ahead}'s actions are listed by {@code branch} and
     * {@code commit}, {@code layout} has only global keys, and {@code start} has the popups of the start screen.
     */
    private static final Set<String> NO_FEATURE = HashSet.of("ahead", "layout", "start");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(PACKAGE);

    /**
     * Each declared feature, with the top-level feature package it is declared in.
     */
    private static List<Tuple2<String, Feature>> declared() {
        return List.ofAll(CLASSES)
                .filter(type -> !type.getPackageName().equals(PACKAGE))
                .flatMap(type -> List.ofAll(type.getFields()))
                .filter(field -> field.getRawType().isEquivalentTo(Feature.class)
                        && field.getModifiers().contains(JavaModifier.STATIC))
                .map(field -> Tuple.of(topLevel(field.getOwner().getPackageName()), value(field)));
    }

    private static String topLevel(String packageName) {
        return packageName.substring(PACKAGE.length() + 1).split("\\.")[0];
    }

    private static Feature value(JavaField field) {
        return Try.of(() -> {
                    Field reflected = field.reflect();
                    reflected.setAccessible(true);
                    return (Feature) reflected.get(null);
                })
                .get();
    }

    @Test
    void eachFeaturePackageDeclaresOneFeature() {
        List<String> packages = declared().map(Tuple2::_1);
        assertEquals(packages.distinct(), packages, "a package declares more than one feature");
    }

    @Test
    void everyFeatureIsListedOnce() {
        List<Feature> features = declared().map(Tuple2::_2);
        assertTrue(features.forAll(feature -> Features.ALL.count(listed -> listed == feature) == 1));
        assertEquals(features.size(), Features.ALL.size());
    }

    @Test
    void onlyThePackagesThatAreNoFeatureDeclareNone() {
        Set<String> all = HashSet.ofAll(CLASSES)
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(PACKAGE + "."))
                .map(FeaturesTest::topLevel);
        Set<String> withFeature = HashSet.ofAll(declared().map(Tuple2::_1));
        assertEquals(NO_FEATURE, all.diff(withFeature));
    }
}
