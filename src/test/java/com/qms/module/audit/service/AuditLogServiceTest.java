package com.qms.module.audit.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.dto.request.AuditSearchRequest;
import com.qms.module.audit.dto.response.AuditLogResponse;
import com.qms.module.audit.dto.response.AuditStatsResponse;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock private AuditLogRepository   auditLogRepository;
    @Mock private AuditValueSerializer valueSerializer;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = AuditLog.builder()
                .id(1L)
                .userId(42L)
                .username("john.doe")
                .userFullName("John Doe")
                .action(AuditAction.CREATE)
                .module(AuditModule.CAPA)
                .entityType("Capa")
                .entityId(17L)
                .description("CAPA created")
                .outcome(AuditOutcome.SUCCESS)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("log()")
    class LogMethod {

        @Test
        @DisplayName("saves the audit log entry to the repository")
        void savesEntry() {
            when(auditLogRepository.save(sampleLog)).thenReturn(sampleLog);

            auditLogService.log(sampleLog);

            verify(auditLogRepository).save(sampleLog);
        }

        @Test
        @DisplayName("never propagates repository exceptions to the caller")
        void absorbsRepositoryException() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

            // Must NOT throw — audit failures are silent
            assertThatCode(() -> auditLogService.log(sampleLog))
                    .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("search()")
    class SearchMethod {

        @Test
        @DisplayName("delegates to repository with correct parameters")
        void delegatesCorrectly() {
            AuditSearchRequest req = new AuditSearchRequest();
            req.setUserId(42L);
            req.setAction(AuditAction.CREATE);
            req.setModule(AuditModule.CAPA);
            req.setPage(0);
            req.setSize(20);

            var pageResult = new PageImpl<>(List.of(sampleLog));
            when(auditLogRepository.search(
                    eq(42L), isNull(), eq(AuditAction.CREATE), eq(AuditModule.CAPA),
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class)
            )).thenReturn(pageResult);

            PageResponse<AuditLogResponse> result = auditLogService.search(req);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1L);
        }

        @Test
        @DisplayName("maps entity fields to response DTO correctly")
        void mapsToResponseCorrectly() {
            AuditSearchRequest req = new AuditSearchRequest();
            var page = new PageImpl<>(List.of(sampleLog));
            when(auditLogRepository.search(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )).thenReturn(page);

            PageResponse<AuditLogResponse> result = auditLogService.search(req);
            AuditLogResponse response = result.getContent().get(0);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUserId()).isEqualTo(42L);
            assertThat(response.getUsername()).isEqualTo("john.doe");
            assertThat(response.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(response.getModule()).isEqualTo(AuditModule.CAPA);
            assertThat(response.getEntityType()).isEqualTo("Capa");
            assertThat(response.getEntityId()).isEqualTo(17L);
            assertThat(response.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getById()")
    class GetByIdMethod {

        @Test
        @DisplayName("returns mapped response when log exists")
        void returnsResponse() {
            when(auditLogRepository.findById(1L)).thenReturn(Optional.of(sampleLog));

            AuditLogResponse result = auditLogService.getById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("john.doe");
        }

        @Test
        @DisplayName("throws NOT_FOUND when log does not exist")
        void throwsNotFound() {
            when(auditLogRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auditLogService.getById(999L))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getByEntity()")
    class GetByEntityMethod {

        @Test
        @DisplayName("returns all audit events for the given entity")
        void returnsEntityHistory() {
            AuditLog log2 = AuditLog.builder()
                    .id(2L).action(AuditAction.UPDATE).module(AuditModule.CAPA)
                    .entityType("Capa").entityId(17L).outcome(AuditOutcome.SUCCESS)
                    .timestamp(LocalDateTime.now()).build();

            when(auditLogRepository.findByEntityTypeAndEntityId("Capa", 17L))
                    .thenReturn(List.of(sampleLog, log2));

            List<AuditLogResponse> result = auditLogService.getByEntity("Capa", 17L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AuditLogResponse::getAction)
                    .containsExactlyInAnyOrder(AuditAction.CREATE, AuditAction.UPDATE);
        }

        @Test
        @DisplayName("returns empty list when entity has no audit history")
        void returnsEmptyList() {
            when(auditLogRepository.findByEntityTypeAndEntityId(any(), any()))
                    .thenReturn(List.of());

            List<AuditLogResponse> result = auditLogService.getByEntity("User", 999L);

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getStats()")
    class GetStatsMethod {

        @Test
        @DisplayName("calculates failure rate correctly")
        void calculatesFailureRate() {
            LocalDateTime since = LocalDateTime.now().minusDays(30);

            when(auditLogRepository.countByOutcomeSince(AuditOutcome.SUCCESS, any())).thenReturn(80L);
            when(auditLogRepository.countByOutcomeSince(AuditOutcome.FAILURE, any())).thenReturn(20L);
            when(auditLogRepository.countByActionSince(AuditAction.LOGIN,        any())).thenReturn(50L);
            when(auditLogRepository.countByActionSince(AuditAction.LOGIN_FAILED, any())).thenReturn(10L);
            when(auditLogRepository.countByModuleSince(any())).thenReturn(List.of());

            AuditStatsResponse stats = auditLogService.getStats(since);

            assertThat(stats.getTotalEvents()).isEqualTo(100L);
            assertThat(stats.getSuccessEvents()).isEqualTo(80L);
            assertThat(stats.getFailureEvents()).isEqualTo(20L);
            assertThat(stats.getFailureRate()).isEqualTo(20.0);
            assertThat(stats.getLoginFailureRate()).isEqualTo(16.67);
        }

        @Test
        @DisplayName("uses last 30 days when since is null")
        void usesDefaultPeriodWhenSinceIsNull() {
            when(auditLogRepository.countByOutcomeSince(any(), any())).thenReturn(0L);
            when(auditLogRepository.countByActionSince(any(), any())).thenReturn(0L);
            when(auditLogRepository.countByModuleSince(any())).thenReturn(List.of());

            AuditStatsResponse stats = auditLogService.getStats(null);

            // Period start should be approximately 30 days ago
            assertThat(stats.getPeriodFrom())
                    .isAfter(LocalDateTime.now().minusDays(31))
                    .isBefore(LocalDateTime.now().minusDays(29));
        }

        @Test
        @DisplayName("handles zero events without division-by-zero")
        void handlesZeroEvents() {
            when(auditLogRepository.countByOutcomeSince(any(), any())).thenReturn(0L);
            when(auditLogRepository.countByActionSince(any(), any())).thenReturn(0L);
            when(auditLogRepository.countByModuleSince(any())).thenReturn(List.of());

            AuditStatsResponse stats = auditLogService.getStats(null);

            assertThat(stats.getFailureRate()).isEqualTo(0.0);
            assertThat(stats.getLoginFailureRate()).isEqualTo(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("record() fluent builder")
    class RecordBuilder {

        @Test
        @DisplayName("save() persists log via the service")
        void builderSavesLog() {
            when(auditLogRepository.save(any())).thenReturn(sampleLog);
            when(valueSerializer.serialize(any())).thenReturn("{\"id\":1}");

            auditLogService.record()
                    .action(AuditAction.APPROVE)
                    .module(AuditModule.DOCUMENT)
                    .entity("Document", 5L)
                    .description("Approved for release")
                    .newValue(new Object())
                    .save();

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(AuditAction.APPROVE);
            assertThat(saved.getModule()).isEqualTo(AuditModule.DOCUMENT);
            assertThat(saved.getEntityType()).isEqualTo("Document");
            assertThat(saved.getEntityId()).isEqualTo(5L);
            assertThat(saved.getDescription()).isEqualTo("Approved for release");
        }
    }
}
