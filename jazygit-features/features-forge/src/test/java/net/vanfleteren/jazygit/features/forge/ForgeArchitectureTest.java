package net.vanfleteren.jazygit.features.forge;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of features-forge: those of the whole application, among which that it names no forge, so that either
 * forge can be deleted without it (ADR 171, 209).
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.features.forge",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ForgeArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
