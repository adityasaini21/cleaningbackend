package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    @Query("""
SELECT u
FROM User u
WHERE u.role = :role
AND (
LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
OR
u.phoneNumber LIKE CONCAT('%', :query, '%')
)
ORDER BY u.fullName
""")
    List<User> searchUsers(
            @Param("role") Role role,
            @Param("query") String query
    );
}