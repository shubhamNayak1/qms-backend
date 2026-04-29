-- V16: rename audit_number → record_number so RecordNumberGenerator works
ALTER TABLE qms_audit_schedule RENAME COLUMN audit_number TO record_number;
