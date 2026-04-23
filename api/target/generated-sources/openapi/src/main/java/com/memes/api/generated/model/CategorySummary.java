package com.memes.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * CategorySummary
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
public class CategorySummary {

  private String category;

  private Integer count;

  private Integer topScore;

  public CategorySummary category(String category) {
    this.category = category;
    return this;
  }

  /**
   * Get category
   * @return category
   */
  
  @JsonProperty("category")
  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public CategorySummary count(Integer count) {
    this.count = count;
    return this;
  }

  /**
   * Get count
   * @return count
   */
  
  @JsonProperty("count")
  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public CategorySummary topScore(Integer topScore) {
    this.topScore = topScore;
    return this;
  }

  /**
   * Get topScore
   * @return topScore
   */
  
  @JsonProperty("top_score")
  public Integer getTopScore() {
    return topScore;
  }

  public void setTopScore(Integer topScore) {
    this.topScore = topScore;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CategorySummary categorySummary = (CategorySummary) o;
    return Objects.equals(this.category, categorySummary.category) &&
        Objects.equals(this.count, categorySummary.count) &&
        Objects.equals(this.topScore, categorySummary.topScore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, count, topScore);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CategorySummary {\n");
    sb.append("    category: ").append(toIndentedString(category)).append("\n");
    sb.append("    count: ").append(toIndentedString(count)).append("\n");
    sb.append("    topScore: ").append(toIndentedString(topScore)).append("\n");
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

