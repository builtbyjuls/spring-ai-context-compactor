package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.model.SpringStereotype;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StereotypeClassifierTest {

    private StereotypeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new StereotypeClassifier();
    }

    private TypeDeclaration<?> parseFirstType(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        return cu.getTypes().getFirst().orElseThrow();
    }

    @Test
    @DisplayName("Classifies @RestController and @Controller as CONTROLLER")
    void classifiesControllers() {
        TypeDeclaration<?> restController = parseFirstType(
                "@org.springframework.web.bind.annotation.RestController public class TradeController {}"
        );
        TypeDeclaration<?> controller = parseFirstType(
                "@Controller public class LegacyTradeController {}"
        );

        assertThat(classifier.classify(restController)).isEqualTo(SpringStereotype.CONTROLLER);
        assertThat(classifier.classify(controller)).isEqualTo(SpringStereotype.CONTROLLER);
    }

    @Test
    @DisplayName("Classifies @Entity, @MappedSuperclass, and @Embeddable as ENTITY")
    void classifiesEntities() {
        TypeDeclaration<?> entity = parseFirstType(
                "@Entity public class ClearedTrade {}"
        );
        TypeDeclaration<?> mappedSuperclass = parseFirstType(
                "@MappedSuperclass public class BaseAuditableEntity {}"
        );
        TypeDeclaration<?> embeddable = parseFirstType(
                "@Embeddable public class MoneyAmount {}"
        );

        assertThat(classifier.classify(entity)).isEqualTo(SpringStereotype.ENTITY);
        assertThat(classifier.classify(mappedSuperclass)).isEqualTo(SpringStereotype.ENTITY);
        assertThat(classifier.classify(embeddable)).isEqualTo(SpringStereotype.ENTITY);
    }

    @Test
    @DisplayName("Classifies @Repository or types extending JpaRepository as REPOSITORY")
    void classifiesRepositories() {
        TypeDeclaration<?> repoAnno = parseFirstType(
                "@Repository public class CustomTradeRepository {}"
        );
        TypeDeclaration<?> jpaRepo = parseFirstType(
                "public interface TradeRepository extends JpaRepository<ClearedTrade, UUID> {}"
        );
        TypeDeclaration<?> crudRepo = parseFirstType(
                "public interface ClearingMemberRepository extends CrudRepository<ClearingMember, Long> {}"
        );

        assertThat(classifier.classify(repoAnno)).isEqualTo(SpringStereotype.REPOSITORY);
        assertThat(classifier.classify(jpaRepo)).isEqualTo(SpringStereotype.REPOSITORY);
        assertThat(classifier.classify(crudRepo)).isEqualTo(SpringStereotype.REPOSITORY);
    }

    @Test
    @DisplayName("Classifies @Configuration and @ConfigurationProperties as CONFIGURATION")
    void classifiesConfigurations() {
        TypeDeclaration<?> config = parseFirstType(
                "@Configuration public class ClearingAmqpConfig {}"
        );
        TypeDeclaration<?> props = parseFirstType(
                "@ConfigurationProperties(prefix = \"trade\") public class TradeProperties {}"
        );

        assertThat(classifier.classify(config)).isEqualTo(SpringStereotype.CONFIGURATION);
        assertThat(classifier.classify(props)).isEqualTo(SpringStereotype.CONFIGURATION);
    }

    @Test
    @DisplayName("Classifies classes with messaging listener methods as MESSAGING_LISTENER")
    void classifiesMessagingListeners() {
        TypeDeclaration<?> rabbitListener = parseFirstType("""
                public class TradeExecutionListener {
                    @RabbitListener(queues = "trades.clearing")
                    public void onMessage(String payload) {}
                }
                """);
        TypeDeclaration<?> kafkaListener = parseFirstType("""
                public class MarginUpdateListener {
                    @KafkaListener(topics = "margins")
                    public void handle(String record) {}
                }
                """);

        assertThat(classifier.classify(rabbitListener)).isEqualTo(SpringStereotype.MESSAGING_LISTENER);
        assertThat(classifier.classify(kafkaListener)).isEqualTo(SpringStereotype.MESSAGING_LISTENER);
    }

    @Test
    @DisplayName("Classifies @Service as SERVICE")
    void classifiesServices() {
        TypeDeclaration<?> service = parseFirstType(
                "@Service public class TradeClearingService {}"
        );

        assertThat(classifier.classify(service)).isEqualTo(SpringStereotype.SERVICE);
    }

    @Test
    @DisplayName("Falls back to COMPONENT for generic components and plain classes")
    void fallsBackToComponent() {
        TypeDeclaration<?> component = parseFirstType(
                "@Component public class SimpleMarginCalculator {}"
        );
        TypeDeclaration<?> plain = parseFirstType(
                "public class StandardSpanMarginCalculator {}"
        );

        assertThat(classifier.classify(component)).isEqualTo(SpringStereotype.COMPONENT);
        assertThat(classifier.classify(plain)).isEqualTo(SpringStereotype.COMPONENT);
    }
}
