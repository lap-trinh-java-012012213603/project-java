package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.FilmStockCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FilmStockCatalogRepository extends JpaRepository<FilmStockCatalog, UUID> {
}