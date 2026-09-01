package pl.jakubtworek.backend_engineering.stage_2.block_a.test.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "pl.jakubtworek.backend_engineering.stage_2.block_a",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class Stage2ArchitectureTest {

    private static final String CLEAN_ARCHITECTURE =
            "..stage_2.block_a.clean_architecture";
    private static final String USE_CASE =
            "..stage_2.block_a.use_case";

    @ArchTest
    static final ArchRule domain_is_independent_from_frameworks = noClasses()
            .that().resideInAnyPackage("..stage_2.block_a..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate.."
            )
            .because("a domain model must remain executable without Spring, JPA or Hibernate");

    @ArchTest
    static final ArchRule clean_architecture_domain_does_not_point_outwards = noClasses()
            .that().resideInAnyPackage(CLEAN_ARCHITECTURE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CLEAN_ARCHITECTURE + ".application..",
                    CLEAN_ARCHITECTURE + ".adapter..",
                    CLEAN_ARCHITECTURE + ".config.."
            )
            .because("dependencies must point towards the clean architecture domain");

    @ArchTest
    static final ArchRule clean_architecture_application_does_not_know_adapters = noClasses()
            .that().resideInAnyPackage(CLEAN_ARCHITECTURE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CLEAN_ARCHITECTURE + ".adapter..",
                    CLEAN_ARCHITECTURE + ".config.."
            )
            .because("application ports and services must not depend on outer adapters");

    @ArchTest
    static final ArchRule canonical_use_case_domain_does_not_point_outwards = noClasses()
            .that().resideInAnyPackage(USE_CASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    USE_CASE + ".application..",
                    USE_CASE + ".adapter..",
                    USE_CASE + ".infrastructure..",
                    USE_CASE + ".integration.."
            )
            .because("the canonical aggregate owns business rules and knows no delivery details");

    @ArchTest
    static final ArchRule canonical_use_case_application_does_not_know_outer_layers = noClasses()
            .that().resideInAnyPackage(USE_CASE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    USE_CASE + ".adapter..",
                    USE_CASE + ".infrastructure..",
                    USE_CASE + ".integration.."
            )
            .because("the use case depends on ports while adapters implement them");

    @ArchTest
    static final ArchRule canonical_models_do_not_depend_on_delivery_technologies = noClasses()
            .that().resideInAnyPackage(
                    "..stage_2.block_a.use_case.domain..",
                    "..stage_2.block_a.use_case.application..",
                    "..stage_2.block_a.reference_flow.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stage_2.block_a.graphql..",
                    "..stage_2.block_a.grpc..",
                    "..stage_2.block_b.websocket..",
                    "..stage_1.block_d.search_engine..",
                    "org.springframework.graphql..",
                    "io.grpc..")
            .because("GraphQL, gRPC, WebSocket and search are replaceable adapters over application ports");
}
