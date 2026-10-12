package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
SELECT u
FROM User u
WHERE u.phoneNumber = :tenDigit
   OR u.phoneNumber = CONCAT('+91', :tenDigit)
   OR u.phoneNumber = CONCAT('91', :tenDigit)
   OR u.phoneNumber = :rawPhone
""")
    Optional<User> findByCanonicalPhone(
            @Param("tenDigit") String tenDigit,
            @Param("rawPhone") String rawPhone
    );

    @Query("""
SELECT (count(u) > 0)
FROM User u
WHERE u.phoneNumber = :tenDigit
   OR u.phoneNumber = CONCAT('+91', :tenDigit)
   OR u.phoneNumber = CONCAT('91', :tenDigit)
   OR u.phoneNumber = :rawPhone
""")
    boolean existsByCanonicalPhone(
            @Param("tenDigit") String tenDigit,
            @Param("rawPhone") String rawPhone
    );

    List<User> findByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    @Query("""
SELECT u
FROM User u
WHERE (
LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
OR
u.phoneNumber LIKE CONCAT('%', :query, '%')
)
ORDER BY u.fullName
""")
    List<User> searchUsers(
            @Param("query") String query
    );

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_by_user boolean DEFAULT false", nativeQuery = true)
    void addDeletedByUserColumnIfNotExists();

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET active = true WHERE active IS NULL", nativeQuery = true)
    void fixNullActiveUsers();


    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET active = true WHERE role = 'ROLE_ADMIN'", nativeQuery = true)
    void ensureAdminsAreActive();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP", nativeQuery = true)
    void addPasswordChangedAtColumnIfNotExists();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(50)", nativeQuery = true)
    void widenPhoneNumberColumn();
}