package com.qms.module.user.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a versioned password policy set by a SUPER_ADMIN.
 *
 * The "active" policy is the row with the most recent effectiveDate
 * that is on or before today.  Previous rows are kept for audit purposes.
 *
 * Field meanings:
 *   passwordLengthMin/Max        – allowed password length bounds
 *   alphaMin                     – minimum number of alphabetic characters
 *   numericMin                   – minimum number of digits
 *   specialCharMin               – minimum number of special characters
 *   upperCaseMin                 – minimum number of uppercase letters
 *   numberOfLoginAttempts        – failed attempts before account lockout
 *   validPeriod                  – days before password expires (0 = never)
 *   previousPasswordAttemptTrack – how many previous hashes to keep and
 *                                  check against (0 = no history check)
 *   effectiveDate                – the date this policy becomes active
 */
@Entity
@Table(name = "password_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordPolicy extends BaseEntity {

    @Column(name = "password_length_min", nullable = false)
    private int passwordLengthMin;

    @Column(name = "password_length_max", nullable = false)
    private int passwordLengthMax;

    @Column(name = "alpha_min", nullable = false)
    private int alphaMin;

    @Column(name = "numeric_min", nullable = false)
    private int numericMin;

    @Column(name = "special_char_min", nullable = false)
    private int specialCharMin;

    @Column(name = "upper_case_min", nullable = false)
    private int upperCaseMin;

    @Column(name = "number_of_login_attempts", nullable = false)
    private int numberOfLoginAttempts;

    /** Days until the password expires.  0 means passwords never expire. */
    @Column(name = "valid_period", nullable = false)
    private int validPeriod;

    /**
     * How many of the user's most-recent password hashes to keep and
     * compare against when the user changes their password.
     * 0 means no history check.
     */
    @Column(name = "previous_password_attempt_track", nullable = false)
    private int previousPasswordAttemptTrack;

    /** The calendar date on which this policy becomes the active policy. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}
