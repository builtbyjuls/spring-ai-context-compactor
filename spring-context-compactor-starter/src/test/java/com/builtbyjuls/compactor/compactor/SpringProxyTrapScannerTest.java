package com.builtbyjuls.compactor.compactor;

import com.builtbyjuls.compactor.model.MethodTrapNoteDto;
import com.builtbyjuls.compactor.model.ProxyTrapType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringProxyTrapScannerTest {

    private SpringProxyTrapScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new SpringProxyTrapScanner();
    }

    private ClassOrInterfaceDeclaration parseClass(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        return cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
    }

    @Test
    @DisplayName("Detects NON_PUBLIC_TRANSACTIONAL on protected and private @Transactional methods")
    void detectsNonPublicTransactional() {
        String code = """
                package com.builtbyjuls.clearing;
                import org.springframework.transaction.annotation.Transactional;

                public class TradeClearingService {
                    @Transactional
                    protected void recordComplianceCheck() {
                    }

                    @Transactional
                    private void updateAuditTrail() {
                    }

                    @Transactional
                    public void publicValidMethod() {
                    }
                }
                """;
        ClassOrInterfaceDeclaration decl = parseClass(code);
        List<MethodTrapNoteDto> traps = scanner.scan(decl);

        assertThat(traps)
                .filteredOn(t -> t.trapType() == ProxyTrapType.NON_PUBLIC_TRANSACTIONAL)
                .extracting(MethodTrapNoteDto::targetMethod)
                .containsExactlyInAnyOrder("recordComplianceCheck", "updateAuditTrail");
    }

    @Test
    @DisplayName("Detects SELF_INVOCATION_BYPASS on intra-class call to @Transactional method")
    void detectsSelfInvocationBypass() {
        String code = """
                package com.builtbyjuls.clearing;
                import org.springframework.transaction.annotation.Transactional;

                public class TradeClearingService {
                    public void processIncomingTrade() {
                        this.persistNovatedTrade();
                        unqualifiedCall();
                    }

                    @Transactional
                    public void persistNovatedTrade() {
                    }

                    @Transactional
                    public void unqualifiedCall() {
                    }
                }
                """;
        ClassOrInterfaceDeclaration decl = parseClass(code);
        List<MethodTrapNoteDto> traps = scanner.scan(decl);

        assertThat(traps)
                .filteredOn(t -> t.trapType() == ProxyTrapType.SELF_INVOCATION_BYPASS)
                .extracting(MethodTrapNoteDto::targetMethod)
                .containsExactlyInAnyOrder("persistNovatedTrade", "unqualifiedCall");
    }

    @Test
    @DisplayName("Detects READ_ONLY_CALLING_WRITE_REQUIRED when readOnly transaction calls write transaction")
    void detectsReadOnlyCallingWriteRequired() {
        String code = """
                package com.builtbyjuls.clearing;
                import org.springframework.transaction.annotation.Transactional;

                public class TradeClearingService {
                    @Transactional(readOnly = true)
                    public void queryAndNovate() {
                        this.writeToLedger();
                    }

                    @Transactional
                    public void writeToLedger() {
                    }
                }
                """;
        ClassOrInterfaceDeclaration decl = parseClass(code);
        List<MethodTrapNoteDto> traps = scanner.scan(decl);

        assertThat(traps)
                .filteredOn(t -> t.trapType() == ProxyTrapType.READ_ONLY_CALLING_WRITE_REQUIRED)
                .hasSize(1)
                .first()
                .satisfies(trap -> {
                    assertThat(trap.callerMethod()).isEqualTo("queryAndNovate");
                    assertThat(trap.targetMethod()).isEqualTo("writeToLedger");
                    assertThat(trap.callSiteLine()).isGreaterThan(0);
                });

        assertThat(traps)
                .filteredOn(t -> t.trapType() == ProxyTrapType.SELF_INVOCATION_BYPASS)
                .hasSize(1);
    }
}
