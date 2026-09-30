package com.receipttrust.trust;

import com.receipttrust.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrustScoreEventRepository extends JpaRepository<TrustScoreEvent, Long> {

    List<TrustScoreEvent> findByUserOrderByCreatedAtDesc(User user);
}
