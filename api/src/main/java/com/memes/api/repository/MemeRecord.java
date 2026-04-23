package com.memes.api.repository;

import java.util.List;

public record MemeRecord(
    String slug,
    String category,
    String title,
    String description,
    String author,
    String subreddit,
    int score,
    String createdAt,
    String sourceUrl,
    String postUrl,
    String imagePath,
    List<String> tags
) {}
