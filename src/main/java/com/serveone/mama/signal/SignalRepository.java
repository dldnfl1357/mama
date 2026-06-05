package com.serveone.mama.signal;

import com.serveone.mama.signal.entity.SignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SignalRepository extends JpaRepository<SignalEntity, String> {

    @Query("""
        SELECT s FROM SignalEntity s
        WHERE s.action IN (com.serveone.mama.signal.Action.BUY,
                           com.serveone.mama.signal.Action.SELL)
          AND s.confidence >= :minConfidence
          AND s.executedAt IS NULL
          AND s.errorMessage IS NULL
        """)
    List<SignalEntity> findExecutable(@Param("minConfidence") double minConfidence);
}
