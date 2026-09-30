package com.receipttrust.friend;

import com.receipttrust.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("""
            select f from Friendship f
            where (f.requester = :a and f.addressee = :b)
               or (f.requester = :b and f.addressee = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") User a, @Param("b") User b);

    List<Friendship> findByAddresseeAndStatus(User addressee, FriendshipStatus status);

    @Query("""
            select f from Friendship f
            where f.status = com.receipttrust.friend.FriendshipStatus.ACCEPTED
              and (f.requester = :user or f.addressee = :user)
            """)
    List<Friendship> findAcceptedForUser(@Param("user") User user);
}
