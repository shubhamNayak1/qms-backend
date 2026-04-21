package com.qms.module.audit.aop;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.service.AuditLogService;
import com.qms.module.audit.service.AuditValueSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditAspect")
class AuditAspectTest {

    @Mock private AuditLogService      auditLogService;
    @Mock private AuditValueSerializer valueSerializer;
    @Mock private ProceedingJoinPoint  joinPoint;
    @Mock private Audited              audited;

    @InjectMocks
    private AuditAspect auditAspect;

    // ── Shared fixture ────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Default @Audited annotation values
        when(audited.action()).thenReturn(AuditAction.CREATE);
        when(audited.module()).thenReturn(AuditModule.CAPA);
        when(audited.entityType()).thenReturn("Capa");
        when(audited.description()).thenReturn("");
        when(audited.captureOldValue()).thenReturn(false);
        when(audited.captureNewValue()).thenReturn(true);
        when(audited.entityIdArgIndex()).thenReturn(-1);
        when(audited.logOnFailure()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("logs SUCCESS outcome and captures return value as new_value")
        void logsSuccessWithNewValue() throws Throwable {
            // Arrange
            record CapaDto(Long id, String title) {}
            CapaDto returnValue = new CapaDto(42L, "Critical Process Deviation");

            when(joinPoint.proceed()).thenReturn(returnValue);
            when(audited.captureNewValue()).thenReturn(true);
            when(valueSerializer.serialize(returnValue)).thenReturn("{\"id\":42,\"title\":\"Critical Process Deviation\"}");

            // Act
            Object result = auditAspect.auditMethod(joinPoint, audited);

            // Assert — result must be passed through untouched
            assertThat(result).isEqualTo(returnValue);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(saved.getModule()).isEqualTo(AuditModule.CAPA);
            assertThat(saved.getEntityType()).isEqualTo("Capa");
            assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(saved.getNewValue()).contains("42");
            assertThat(saved.getErrorType()).isNull();
        }

        @Test
        @DisplayName("extracts entity ID from return value using getId()")
        void extractsEntityIdFromReturnValue() throws Throwable {
            // Arrange — object with getId()
            class Entity { public Long getId() { return 99L; } }
            when(joinPoint.proceed()).thenReturn(new Entity());
            when(audited.entityIdArgIndex()).thenReturn(-1);
            when(valueSerializer.serialize(any())).thenReturn("{}");

            // Act
            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getEntityId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("extracts entity ID from method argument when entityIdArgIndex is set")
        void extractsEntityIdFromArg() throws Throwable {
            when(audited.entityIdArgIndex()).thenReturn(0);
            when(joinPoint.getArgs()).thenReturn(new Object[]{77L, "other-arg"});
            when(joinPoint.proceed()).thenReturn(null);

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getEntityId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("captures old value from first argument when captureOldValue = true")
        void capturesOldValue() throws Throwable {
            Object oldArg = new Object();
            when(audited.captureOldValue()).thenReturn(true);
            when(joinPoint.getArgs()).thenReturn(new Object[]{oldArg});
            when(joinPoint.proceed()).thenReturn(null);
            when(valueSerializer.serializeIfPresent(oldArg)).thenReturn("{\"old\":true}");

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getOldValue()).isEqualTo("{\"old\":true}");
        }

        @Test
        @DisplayName("does NOT capture new value when captureNewValue = false")
        void suppressNewValueCapture() throws Throwable {
            when(audited.captureNewValue()).thenReturn(false);
            when(joinPoint.proceed()).thenReturn("some-result");

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getNewValue()).isNull();
            verify(valueSerializer, never()).serialize(any());
        }

        @Test
        @DisplayName("records duration in milliseconds")
        void recordsDuration() throws Throwable {
            when(joinPoint.proceed()).thenReturn(null);

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Failure path")
    class FailurePath {

        @Test
        @DisplayName("logs FAILURE outcome and re-throws the original exception")
        void logsFailureAndRethrows() throws Throwable {
            RuntimeException ex = new RuntimeException("Database error");
            when(joinPoint.proceed()).thenThrow(ex);
            when(audited.logOnFailure()).thenReturn(true);

            // Act & assert — exception must propagate
            assertThatThrownBy(() -> auditAspect.auditMethod(joinPoint, audited))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
            assertThat(saved.getErrorType()).contains("RuntimeException");
            assertThat(saved.getErrorMessage()).isEqualTo("Database error");
        }

        @Test
        @DisplayName("does NOT log when logOnFailure = false and method throws")
        void skipsLoggingWhenLogOnFailureIsFalse() throws Throwable {
            when(audited.logOnFailure()).thenReturn(false);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> auditAspect.auditMethod(joinPoint, audited))
                    .isInstanceOf(RuntimeException.class);

            verify(auditLogService, never()).log(any());
        }

        @Test
        @DisplayName("still returns result when audit logging itself throws")
        void doesNotCrashWhenAuditServiceFails() throws Throwable {
            // Audit service throws — business method should still succeed
            when(joinPoint.proceed()).thenReturn("business-result");
            doThrow(new RuntimeException("DB unavailable"))
                    .when(auditLogService).log(any());
            when(valueSerializer.serialize(any())).thenReturn("{}");

            // Should NOT propagate the audit failure
            Object result = auditAspect.auditMethod(joinPoint, audited);
            assertThat(result).isEqualTo("business-result");
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Description generation")
    class DescriptionGeneration {

        @Test
        @DisplayName("uses annotation description when provided")
        void usesAnnotationDescription() throws Throwable {
            when(audited.description()).thenReturn("Custom description");
            when(joinPoint.proceed()).thenReturn(null);

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getDescription()).isEqualTo("Custom description");
        }

        @Test
        @DisplayName("auto-generates description when annotation description is blank")
        void autoGeneratesDescription() throws Throwable {
            when(audited.description()).thenReturn("");
            when(audited.entityType()).thenReturn("User");
            when(joinPoint.proceed()).thenReturn(null);

            auditAspect.auditMethod(joinPoint, audited);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).log(captor.capture());
            assertThat(captor.getValue().getDescription()).contains("CREATE").contains("User");
        }
    }
}
