package pl.jakubtworek.marketplace.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Wykonywalna dependency rule. Test tekstowy wykrywał tylko kilka importów i można
 * go było ominąć przez adnotację złożoną albo zależność tranzytywną. ArchUnit bada
 * skompilowany graf klas, więc architektura jest kontraktem CI, a nie diagramem.
 */
@AnalyzeClasses(packages = "pl.jakubtworek.marketplace", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule DOMAIN_POINTS_INWARD = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..");

    @ArchTest
    static final ArchRule ORDERING_DOMAIN_DOES_NOT_IMPORT_OTHER_CONTEXT_MODELS = noClasses()
            .that().resideInAPackage("..ordering.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..catalog.domain..", "..payment.domain..", "..inventory.domain..", "..fulfillment.domain..");

    @ArchTest
    static final ArchRule PAYMENT_AND_INVENTORY_DOMAINS_DO_NOT_IMPORT_ORDERING = noClasses()
            .that().resideInAnyPackage("..payment.domain..", "..inventory.domain..")
            .should().dependOnClassesThat().resideInAPackage("..ordering.domain..");

    @ArchTest
    static final ArchRule ORDERING_APPLICATION_USES_PUBLISHED_LANGUAGE = noClasses()
            .that().resideInAPackage("..ordering.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..catalog.domain..", "..payment.domain..", "..inventory.domain..", "..fulfillment.domain..")
            .because("cross-module communication must use integration.contracts");

    @ArchTest
    static final ArchRule PAYMENT_AND_INVENTORY_APPLICATIONS_USE_PUBLISHED_LANGUAGE = noClasses()
            .that().resideInAnyPackage("..payment.application..", "..inventory.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..ordering.domain..", "..fulfillment.domain..")
            .because("cross-module communication must use integration.contracts");

    @ArchTest
    static final ArchRule DOWNSTREAM_APPLICATIONS_USE_PUBLISHED_LANGUAGE = noClasses()
            .that().resideInAnyPackage("..fulfillment.application..", "..notification.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ordering.domain..", "..payment.domain..", "..inventory.domain..")
            .because("cross-module communication must use integration.contracts");

    @ArchTest
    static final ArchRule CONTROLLERS_LIVE_ONLY_IN_API = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..api..");
}
