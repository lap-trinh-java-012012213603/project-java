package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, UUID>, JpaSpecificationExecutor<WalletLedgerEntry> {

    List<WalletLedgerEntry> findByJournalGroupIdOrderByCreatedAtAsc(UUID journalGroupId);

    Page<WalletLedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    @Query("""
            SELECT e FROM WalletLedgerEntry e
            WHERE e.walletId = :walletId
              AND (:entryDirection IS NULL OR e.entryDirection = :entryDirection)
              AND (:entryType IS NULL OR e.entryType = :entryType)
              AND (:fromTime IS NULL OR e.createdAt >= :fromTime)
              AND (:toTime IS NULL OR e.createdAt <= :toTime)
            ORDER BY e.createdAt DESC
            """)
    Page<WalletLedgerEntry> searchWalletLedger(
            @Param("walletId") UUID walletId,
            @Param("entryDirection") LedgerEntryDirection entryDirection,
            @Param("entryType") LedgerEntryType entryType,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );
}
