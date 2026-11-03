package com.analoghub.platform.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = LabSearchDocument.INDEX_NAME, createIndex = false)
public class LabSearchDocument {

    public static final String INDEX_NAME = "analoghub_labs_v1";

    @Id
    @Field(name = "labId", type = FieldType.Keyword)
    private String labId;

    @Field(type = FieldType.Keyword)
    private String slug;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer"),
            otherFields = {
                    @InnerField(
                            suffix = "autocomplete",
                            type = FieldType.Text,
                            analyzer = "vi_autocomplete_index_analyzer",
                            searchAnalyzer = "vi_folded_analyzer"
                    ),
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer")
    private String bio;

    @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer")
    private String fullAddress;

    @Field(type = FieldType.Keyword)
    private String districtCode;

    @Field(type = FieldType.Keyword)
    private String provinceCode;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Keyword, index = false)
    private String coverImageUrl;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private List<String> supportedFormats;

    @Field(type = FieldType.Keyword)
    private List<String> supportedProcesses;

    @Field(type = FieldType.Keyword)
    private List<String> scannerModels;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal minPriceVnd;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal maxPriceVnd;

    @Field(type = FieldType.Integer)
    private Integer estimatedTurnaroundHours;

    @Field(type = FieldType.Boolean)
    private Boolean isAcceptingOrders;

    @Field(type = FieldType.Float)
    private Float avgRating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;
}

