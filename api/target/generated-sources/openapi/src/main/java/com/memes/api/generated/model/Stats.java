package com.memes.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Stats
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
public class Stats {

  private Integer totalMemes;

  private Integer totalCategories;

  private Integer totalSubreddits;

  private String topCategory;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime indexedAt;

  public Stats totalMemes(Integer totalMemes) {
    this.totalMemes = totalMemes;
    return this;
  }

  /**
   * Get totalMemes
   * @return totalMemes
   */
  
  @JsonProperty("total_memes")
  public Integer getTotalMemes() {
    return totalMemes;
  }

  public void setTotalMemes(Integer totalMemes) {
    this.totalMemes = totalMemes;
  }

  public Stats totalCategories(Integer totalCategories) {
    this.totalCategories = totalCategories;
    return this;
  }

  /**
   * Get totalCategories
   * @return totalCategories
   */
  
  @JsonProperty("total_categories")
  public Integer getTotalCategories() {
    return totalCategories;
  }

  public void setTotalCategories(Integer totalCategories) {
    this.totalCategories = totalCategories;
  }

  public Stats totalSubreddits(Integer totalSubreddits) {
    this.totalSubreddits = totalSubreddits;
    return this;
  }

  /**
   * Get totalSubreddits
   * @return totalSubreddits
   */
  
  @JsonProperty("total_subreddits")
  public Integer getTotalSubreddits() {
    return totalSubreddits;
  }

  public void setTotalSubreddits(Integer totalSubreddits) {
    this.totalSubreddits = totalSubreddits;
  }

  public Stats topCategory(String topCategory) {
    this.topCategory = topCategory;
    return this;
  }

  /**
   * Get topCategory
   * @return topCategory
   */
  
  @JsonProperty("top_category")
  public String getTopCategory() {
    return topCategory;
  }

  public void setTopCategory(String topCategory) {
    this.topCategory = topCategory;
  }

  public Stats indexedAt(OffsetDateTime indexedAt) {
    this.indexedAt = indexedAt;
    return this;
  }

  /**
   * Get indexedAt
   * @return indexedAt
   */
  @Valid 
  @JsonProperty("indexed_at")
  public OffsetDateTime getIndexedAt() {
    return indexedAt;
  }

  public void setIndexedAt(OffsetDateTime indexedAt) {
    this.indexedAt = indexedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Stats stats = (Stats) o;
    return Objects.equals(this.totalMemes, stats.totalMemes) &&
        Objects.equals(this.totalCategories, stats.totalCategories) &&
        Objects.equals(this.totalSubreddits, stats.totalSubreddits) &&
        Objects.equals(this.topCategory, stats.topCategory) &&
        Objects.equals(this.indexedAt, stats.indexedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalMemes, totalCategories, totalSubreddits, topCategory, indexedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Stats {\n");
    sb.append("    totalMemes: ").append(toIndentedString(totalMemes)).append("\n");
    sb.append("    totalCategories: ").append(toIndentedString(totalCategories)).append("\n");
    sb.append("    totalSubreddits: ").append(toIndentedString(totalSubreddits)).append("\n");
    sb.append("    topCategory: ").append(toIndentedString(topCategory)).append("\n");
    sb.append("    indexedAt: ").append(toIndentedString(indexedAt)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

