package com.qms.module.lms.service;

import com.qms.common.exception.AppException;
import com.qms.module.lms.dto.request.AttendanceRequest;
import com.qms.module.lms.dto.request.TrainingSessionRequest;
import com.qms.module.lms.dto.response.TrainingSessionResponse;
import com.qms.module.lms.entity.TrainingAttendance;
import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.entity.TrainingSession;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.enums.SessionStatus;
import com.qms.module.lms.repository.EnrollmentRepository;
import com.qms.module.lms.repository.TrainingAttendanceRepository;
import com.qms.module.lms.repository.TrainingProgramRepository;
import com.qms.module.lms.repository.TrainingSessionRepository;
import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.module.audit.annotation.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingSessionService {

    private final TrainingSessionRepository    sessionRepository;
    private final TrainingProgramRepository    programRepository;
    private final TrainingAttendanceRepository attendanceRepository;
    private final EnrollmentRepository         enrollmentRepository;

    // ── Queries ──────────────────────────────────────────────

    public List<TrainingSessionResponse> getByProgram(Long programId) {
        return sessionRepository.findByProgram_IdOrderBySessionDateAsc(programId)
                .stream().map(this::toResponse).toList();
    }

    public TrainingSessionResponse getById(Long sessionId) {
        return toResponseWithAttendance(findSessionById(sessionId));
    }

    // ── Create ───────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.TRAINING, entityType = "TrainingSession",
             entityIdArgIndex = 0, description = "Training session created")
    @Transactional
    public TrainingSessionResponse create(Long programId, TrainingSessionRequest req) {
        TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(programId)
                .orElseThrow(() -> AppException.notFound("Training Program", programId));

        if (program.getStatus() != ProgramStatus.APPROVED
                && program.getStatus() != ProgramStatus.PLANNED
                && program.getStatus() != ProgramStatus.ACTIVE) {
            throw AppException.badRequest(
                    "Sessions can only be added to APPROVED, PLANNED, or ACTIVE programs. Current: "
                    + program.getStatus());
        }

        TrainingSession session = TrainingSession.builder()
                .program(program)
                .sessionDate(req.getSessionDate())
                .sessionEndDate(req.getSessionEndDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .venue(req.getVenue())
                .meetingLink(req.getMeetingLink())
                .trainerId(req.getTrainerId() != null ? req.getTrainerId()   : program.getTrainerId())
                .trainerName(req.getTrainerName() != null ? req.getTrainerName() : program.getTrainerName())
                .coordinatorId(req.getCoordinatorId() != null ? req.getCoordinatorId() : program.getCoordinatorId())
                .coordinatorName(req.getCoordinatorName() != null ? req.getCoordinatorName() : program.getCoordinatorName())
                .maxParticipants(req.getMaxParticipants())
                .notes(req.getNotes())
                .status(SessionStatus.SCHEDULED)
                .createdBy(currentUsername())
                .build();

        TrainingSession saved = sessionRepository.save(session);
        log.info("Session created for program {} on {}", programId, req.getSessionDate());
        return toResponse(saved);
    }

    // ── Update ───────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingSession",
             entityIdArgIndex = 0, description = "Training session updated")
    @Transactional
    public TrainingSessionResponse update(Long sessionId, TrainingSessionRequest req) {
        TrainingSession session = findSessionById(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED) {
            throw AppException.badRequest("Cannot update a " + session.getStatus() + " session");
        }
        if (req.getSessionDate()      != null) session.setSessionDate(req.getSessionDate());
        if (req.getSessionEndDate()   != null) session.setSessionEndDate(req.getSessionEndDate());
        if (req.getStartTime()        != null) session.setStartTime(req.getStartTime());
        if (req.getEndTime()          != null) session.setEndTime(req.getEndTime());
        if (req.getVenue()            != null) session.setVenue(req.getVenue());
        if (req.getMeetingLink()      != null) session.setMeetingLink(req.getMeetingLink());
        if (req.getTrainerId()        != null) session.setTrainerId(req.getTrainerId());
        if (req.getTrainerName()      != null) session.setTrainerName(req.getTrainerName());
        if (req.getCoordinatorId()    != null) session.setCoordinatorId(req.getCoordinatorId());
        if (req.getCoordinatorName()  != null) session.setCoordinatorName(req.getCoordinatorName());
        if (req.getMaxParticipants()  != null) session.setMaxParticipants(req.getMaxParticipants());
        if (req.getNotes()            != null) session.setNotes(req.getNotes());
        return toResponse(sessionRepository.save(session));
    }

    // ── Status transitions ───────────────────────────────────

    @Audited(action = AuditAction.CANCEL, module = AuditModule.TRAINING, entityType = "TrainingSession",
             entityIdArgIndex = 0, description = "Training session cancelled")
    @Transactional
    public TrainingSessionResponse cancel(Long sessionId, String reason) {
        TrainingSession session = findSessionById(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw AppException.badRequest("Cannot cancel a COMPLETED session");
        }
        session.setStatus(SessionStatus.CANCELLED);
        session.setCancellationReason(reason);
        log.info("Session {} cancelled", sessionId);
        return toResponse(sessionRepository.save(session));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingSession",
             entityIdArgIndex = 0, description = "Training session marked COMPLETED")
    @Transactional
    public TrainingSessionResponse complete(Long sessionId) {
        TrainingSession session = findSessionById(sessionId);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw AppException.badRequest("Cannot complete a CANCELLED session");
        }
        session.setStatus(SessionStatus.COMPLETED);
        log.info("Session {} marked as COMPLETED", sessionId);
        return toResponseWithAttendance(sessionRepository.save(session));
    }

    // ── Attendance ───────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.TRAINING, entityType = "TrainingSession",
             entityIdArgIndex = 0, description = "Attendance marked for training session")
    @Transactional
    public TrainingSessionResponse markAttendance(Long sessionId, AttendanceRequest req) {
        TrainingSession session = findSessionById(sessionId);

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw AppException.badRequest("Cannot mark attendance for a CANCELLED session");
        }
        if (!session.isWithinAttendanceWindow()) {
            throw AppException.badRequest(
                    "Attendance can only be marked within ±2 days of the session date ("
                    + session.getSessionDate() + ")");
        }

        String markedBy = currentUsername();
        LocalDate today  = LocalDate.now();

        for (AttendanceRequest.AttendeeRecord a : req.getAttendees()) {
            TrainingAttendance attendance = attendanceRepository
                    .findBySession_IdAndEnrollmentId(sessionId, a.getEnrollmentId())
                    .orElseGet(() -> TrainingAttendance.builder()
                            .session(session)
                            .enrollmentId(a.getEnrollmentId())
                            .userId(a.getUserId())
                            .build());

            attendance.setUserName(a.getUserName());
            attendance.setIsPresent(a.getIsPresent());
            attendance.setAttendanceDate(a.getAttendanceDate() != null ? a.getAttendanceDate() : today);
            attendance.setNotes(a.getNotes());
            attendance.setMarkedBy(markedBy);
            attendance.setMarkedAt(LocalDateTime.now());
            attendanceRepository.save(attendance);

            // Move enrollment to IN_PROGRESS when trainee is present
            if (Boolean.TRUE.equals(a.getIsPresent())) {
                enrollmentRepository.findByIdAndIsDeletedFalse(a.getEnrollmentId())
                        .ifPresent(e -> {
                            if (e.getStatus() == EnrollmentStatus.ALLOCATED
                                    || e.getStatus() == EnrollmentStatus.ENROLLED) {
                                e.setStatus(EnrollmentStatus.IN_PROGRESS);
                                e.setStartedAt(LocalDateTime.now());
                                e.setAttendanceMarked(true);
                                e.setAttendanceDate(attendance.getAttendanceDate());
                                enrollmentRepository.save(e);
                            }
                        });
            }
        }

        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }

        log.info("Attendance marked for session {} by {}", sessionId, markedBy);
        return toResponseWithAttendance(session);
    }

    // ── Helpers ──────────────────────────────────────────────

    private TrainingSession findSessionById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Training Session", id));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private TrainingSessionResponse toResponse(TrainingSession s) {
        long presentCount = s.getId() != null ? attendanceRepository.countPresentBySession(s.getId()) : 0;
        long totalEnrolled = s.getProgram() != null && s.getProgram().getId() != null
                ? enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                        s.getProgram().getId(), EnrollmentStatus.ALLOCATED)
                  + enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                        s.getProgram().getId(), EnrollmentStatus.IN_PROGRESS)
                  + enrollmentRepository.countByProgram_IdAndStatusAndIsDeletedFalse(
                        s.getProgram().getId(), EnrollmentStatus.ENROLLED)
                : 0;

        return TrainingSessionResponse.builder()
                .id(s.getId())
                .programId(s.getProgram() != null ? s.getProgram().getId() : null)
                .programCode(s.getProgram() != null ? s.getProgram().getCode() : null)
                .programTitle(s.getProgram() != null ? s.getProgram().getTitle() : null)
                .sessionDate(s.getSessionDate())
                .sessionEndDate(s.getSessionEndDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .venue(s.getVenue())
                .meetingLink(s.getMeetingLink())
                .trainerId(s.getTrainerId())
                .trainerName(s.getTrainerName())
                .coordinatorId(s.getCoordinatorId())
                .coordinatorName(s.getCoordinatorName())
                .maxParticipants(s.getMaxParticipants())
                .status(s.getStatus())
                .cancellationReason(s.getCancellationReason())
                .notes(s.getNotes())
                .withinAttendanceWindow(s.isWithinAttendanceWindow())
                .presentCount(presentCount)
                .totalEnrolled(totalEnrolled)
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .build();
    }

    private TrainingSessionResponse toResponseWithAttendance(TrainingSession s) {
        List<TrainingSessionResponse.AttendanceSummary> attendanceSummaries =
                attendanceRepository.findBySession_Id(s.getId()).stream()
                        .map(a -> TrainingSessionResponse.AttendanceSummary.builder()
                                .id(a.getId())
                                .userId(a.getUserId())
                                .userName(a.getUserName())
                                .enrollmentId(a.getEnrollmentId())
                                .isPresent(a.getIsPresent())
                                .attendanceDate(a.getAttendanceDate())
                                .markedBy(a.getMarkedBy())
                                .notes(a.getNotes())
                                .build())
                        .toList();

        TrainingSessionResponse resp = toResponse(s);
        resp.setAttendance(attendanceSummaries);
        return resp;
    }
}
