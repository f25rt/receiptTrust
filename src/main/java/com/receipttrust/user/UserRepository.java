package com.receipttrust.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Case-insensitive search across full name, username, and email. */
    @org.springframework.data.jpa.repository.Query("""
            select u from User u
            where lower(u.fullName) like lower(concat('%', :q, '%'))
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.email)    like lower(concat('%', :q, '%'))
            order by u.fullName asc
            """)
    List<User> searchByNameOrUsernameOrEmail(
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    /** Users whose profile image was uploaded before the cutoff. */
    @org.springframework.data.jpa.repository.Query("""
            select u from User u
            where u.profileImagePath is not null
              and u.profileImageUploadedAt is not null
              and u.profileImageUploadedAt < :cutoff
            """)
    List<User> findProfileImageOlderThan(
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
