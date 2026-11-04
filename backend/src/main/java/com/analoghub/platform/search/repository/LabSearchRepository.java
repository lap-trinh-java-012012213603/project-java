package com.analoghub.platform.search.repository;

import com.analoghub.platform.search.document.LabSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabSearchRepository extends ElasticsearchRepository<LabSearchDocument, String> {

    Optional<LabSearchDocument> findBySlug(String slug);

    List<LabSearchDocument> findByStatus(String status);

    List<LabSearchDocument> findByStatusAndProvinceCode(String status, String provinceCode);
}

