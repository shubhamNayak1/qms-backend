package com.qms.module.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Stores BCrypt hashes of a user's previous passwords.
 * Used to enforce the "previousPasswordAttemptTrack" rule in PasswordPolicy:
 * the user may not reuse any of their last N passwords.
 *
 * Rows are pruned automatically whenever a new password is saved —
 * only the most-recent N hashes are kept (N = policy.previousPasswordAttemptTrack).
 */
@Entity
@Table(name = "password_histories")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
