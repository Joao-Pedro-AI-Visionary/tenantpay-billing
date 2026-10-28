package com.tenantpay.billing.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Clean Architecture boundaries, checked on every build. */
class ArchitectureTest {
    private static final String ROOT = "com.tenantpay.billing";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";
    private static final String WEB = ROOT + ".web..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void domainDependsOnlyOnTheJdk() {
        classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
                .check(classes);
    }

    @Test
    void applicationDependsOnlyOnTheJdkAndDomain() {
        classes().that().resideInAPackage(APPLICATION)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN, APPLICATION)
                .check(classes);
    }

    @Test
    void webDoesNotTouchInfrastructure() {
        noClasses().that().resideInAPackage(WEB)
                .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
                .check(classes);
    }

    @Test
    void infrastructureDoesNotTouchWeb() {
        noClasses().that().resideInAPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAPackage(WEB)
                .check(classes);
    }

    @Test
    void layersAreRespected() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Web").definedBy(WEB)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Web", "Infrastructure")
                .check(classes);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }
}
