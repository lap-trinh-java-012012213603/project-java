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
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = ListingSearchDocument.INDEX_NAME, createIndex = false)
public class ListingSearchDocument {

    public static final String INDEX_NAME = "analoghub_listings_v1";

    @Id
    @Field(name = "listingId", type = FieldType.Keyword)
    private String listingId;

    @Field(type = FieldType.Keyword)
    private String sellerUserId;

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
    private String title;

    @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @MultiField(
            mainField = @Field(type = FieldType.Keyword),
            otherFields = {
                    @InnerField(suffix = "normalized", type = FieldType.Keyword, normalizer = "lowercase_normalizer")
            }
    )
    private String brand;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String modelName;

    @Field(type = FieldType.Keyword)
    private String conditionGrade;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal priceVnd;

    @Field(type = FieldType.Boolean)
    private Boolean allowPriceOffer;

    @Field(type = FieldType.Boolean)
    private Boolean allowSwap;

    @Field(type = FieldType.Text, analyzer = "vi_folded_analyzer")
    private String preferredSwapItems;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword)
    private String provinceCode;

    @Field(type = FieldType.Keyword)
    private String districtCode;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;
}

