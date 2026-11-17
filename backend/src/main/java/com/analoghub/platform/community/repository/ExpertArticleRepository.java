package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.ArticleCategory;
import com.analoghub.platform.community.domain.ArticleStatus;
import com.analoghub.platform.community.domain.ExpertArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpertArticleRepository extends JpaRepository<ExpertArticle, UUID> {

    Optional<ExpertArticle> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExpertArticle> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlug(String slug);

    List<ExpertArticle> findByStatusAndApprovedForRagTrueAndDeletedAtIsNull(ArticleStatus status);

    @Query("""
            SELECT a FROM ExpertArticle a
            WHERE a.status = :status
              AND a.deletedAt IS NULL
              AND (:category IS NULL OR a.category = :category)
              AND (:authorExpertId IS NULL OR a.authorExpertId = :authorExpertId)
              AND (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY a.createdAt DESC
            """)
    Page<ExpertArticle> searchPublishedArticles(
            @Param("status") ArticleStatus status,
            @Param("category") ArticleCategory category,
            @Param("authorExpertId") UUID authorExpertId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
