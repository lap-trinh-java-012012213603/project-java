package com.analoghub.platform.filmlab.controller;

import com.analoghub.platform.filmlab.domain.FilmStockCatalog;
import com.analoghub.platform.filmlab.repository.FilmStockCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/film-stocks")
@RequiredArgsConstructor
public class FilmStockCatalogController {

    private final FilmStockCatalogRepository filmStockCatalogRepository;

    @GetMapping
    public ResponseEntity<List<FilmStockCatalog>> getAllFilmStocks() {
        // Có thể bổ sung Service layer sau nếu logic phức tạp hơn
        List<FilmStockCatalog> catalogs = filmStockCatalogRepository.findAll();
        return ResponseEntity.ok(catalogs);
    }
}