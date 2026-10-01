package com.receipttrust.message;

import com.receipttrust.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /** Full conversation between two users, oldest first. */
    @Query("""
            select m from DirectMessage m
            where (m.sender.id = :a and m.recipient.id = :b)
               or (m.sender.id = :b and m.recipient.id = :a)
            order by m.createdAt asc
            """)
    List<DirectMessage> findConversation(@Param("a") Long a, @Param("b") Long b);

    /** Mark every message from :other to :me as read. */
    @Modifying
    @Query("""
            update DirectMessage m
            set m.readByRecipient = true
            where m.recipient.id = :me and m.sender.id = :other and m.readByRecipient = false
            """)
    int markConversationRead(@Param("me") Long me, @Param("other") Long other);

    long countByRecipientAndReadByRecipientFalse(User recipient);
}
