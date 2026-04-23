package com.memes.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ReindexResult
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
public class ReindexResult {

  private Integer indexed;

  private Long durationMs;

  @Valid
  private List<String> errors = new ArrayList<>();

  public ReindexResult indexed(Integer indexed) {
    this.indexed = indexed;
    return this;
  }

  /**
   * Get indexed
   * @return indexed
   */
  
  @JsonProperty("indexed")
  public Integer getIndexed() {
    return indexed;
  }

  public void setIndexed(Integer indexed) {
    this.indexed = indexed;
  }

  public ReindexResult durationMs(Long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  /**
   * Get durationMs
   * @return durationMs
   */
  
  @JsonProperty("duration_ms")
  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public ReindexResult errors(List<String> errors) {
    this.errors = errors;
    return this;
  }

  public ReindexResult addErrorsItem(String errorsItem) {
    if (this.errors == null) {
      this.errors = new ArrayList<>();
    }
    this.errors.add(errorsItem);
    return this;
  }

  /**
   * Get errors
   * @return errors
   */
  
  @JsonProperty("errors")
  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReindexResult reindexResult = (ReindexResult) o;
    return Objects.equals(this.indexed, reindexResult.indexed) &&
        Objects.equals(this.durationMs, reindexResult.durationMs) &&
        Objects.equals(this.errors, reindexResult.errors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexed, durationMs, errors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ReindexResult {\n");
    sb.append("    indexed: ").append(toIndentedString(indexed)).append("\n");
    sb.append("    durationMs: ").append(toIndentedString(durationMs)).append("\n");
    sb.append("    errors: ").append(toIndentedString(errors)).append("\n");
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

