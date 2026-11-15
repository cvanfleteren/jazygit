package net.vanfleteren.jazygit.github;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-github: those of the whole application.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.github", importOptions = ImportOption.DoNotIncludeTests.class)
class GitHubArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
