package com.memes.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Meme
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
public class Meme {

  private String slug;

  private String category;

  private String title;

  private String description;

  private String author;

  private String subreddit;

  private Integer score;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  private String sourceUrl;

  private String postUrl;

  private String imagePath;

  @Valid
  private List<String> tags = new ArrayList<>();

  public Meme() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Meme(String slug, String category, String title) {
    this.slug = slug;
    this.category = category;
    this.title = title;
  }

  public Meme slug(String slug) {
    this.slug = slug;
    return this;
  }

  /**
   * Get slug
   * @return slug
   */
  @NotNull 
  @JsonProperty("slug")
  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public Meme category(String category) {
    this.category = category;
    return this;
  }

  /**
   * Get category
   * @return category
   */
  @NotNull 
  @JsonProperty("category")
  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Meme title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  @NotNull 
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Meme description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description
   * @return description
   */
  
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Meme author(String author) {
    this.author = author;
    return this;
  }

  /**
   * Get author
   * @return author
   */
  
  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public Meme subreddit(String subreddit) {
    this.subreddit = subreddit;
    return this;
  }

  /**
   * Get subreddit
   * @return subreddit
   */
  
  @JsonProperty("subreddit")
  public String getSubreddit() {
    return subreddit;
  }

  public void setSubreddit(String subreddit) {
    this.subreddit = subreddit;
  }

  public Meme score(Integer score) {
    this.score = score;
    return this;
  }

  /**
   * Get score
   * @return score
   */
  
  @JsonProperty("score")
  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }

  public Meme createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @Valid 
  @JsonProperty("created_at")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public Meme sourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
    return this;
  }

  /**
   * Get sourceUrl
   * @return sourceUrl
   */
  
  @JsonProperty("source_url")
  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public Meme postUrl(String postUrl) {
    this.postUrl = postUrl;
    return this;
  }

  /**
   * Get postUrl
   * @return postUrl
   */
  
  @JsonProperty("post_url")
  public String getPostUrl() {
    return postUrl;
  }

  public void setPostUrl(String postUrl) {
    this.postUrl = postUrl;
  }

  public Meme imagePath(String imagePath) {
    this.imagePath = imagePath;
    return this;
  }

  /**
   * Get imagePath
   * @return imagePath
   */
  
  @JsonProperty("image_path")
  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public Meme tags(List<String> tags) {
    this.tags = tags;
    return this;
  }

  public Meme addTagsItem(String tagsItem) {
    if (this.tags == null) {
      this.tags = new ArrayList<>();
    }
    this.tags.add(tagsItem);
    return this;
  }

  /**
   * Get tags
   * @return tags
   */
  
  @JsonProperty("tags")
  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Meme meme = (Meme) o;
    return Objects.equals(this.slug, meme.slug) &&
        Objects.equals(this.category, meme.category) &&
        Objects.equals(this.title, meme.title) &&
        Objects.equals(this.description, meme.description) &&
        Objects.equals(this.author, meme.author) &&
        Objects.equals(this.subreddit, meme.subreddit) &&
        Objects.equals(this.score, meme.score) &&
        Objects.equals(this.createdAt, meme.createdAt) &&
        Objects.equals(this.sourceUrl, meme.sourceUrl) &&
        Objects.equals(this.postUrl, meme.postUrl) &&
        Objects.equals(this.imagePath, meme.imagePath) &&
        Objects.equals(this.tags, meme.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slug, category, title, description, author, subreddit, score, createdAt, sourceUrl, postUrl, imagePath, tags);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Meme {\n");
    sb.append("    slug: ").append(toIndentedString(slug)).append("\n");
    sb.append("    category: ").append(toIndentedString(category)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    author: ").append(toIndentedString(author)).append("\n");
    sb.append("    subreddit: ").append(toIndentedString(subreddit)).append("\n");
    sb.append("    score: ").append(toIndentedString(score)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    sourceUrl: ").append(toIndentedString(sourceUrl)).append("\n");
    sb.append("    postUrl: ").append(toIndentedString(postUrl)).append("\n");
    sb.append("    imagePath: ").append(toIndentedString(imagePath)).append("\n");
    sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
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

