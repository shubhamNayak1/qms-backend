-- V13: Make enrollment_id nullable in lms_training_attendance
-- Allows marking attendance by userId alone when enrollmentId is not known by the caller.
-- The service layer auto-resolves enrollmentId from userId + programId when possible.

ALTER TABLE lms_training_attendance
    ALTER COLUMN enrollment_id DROP NOT NULL;
