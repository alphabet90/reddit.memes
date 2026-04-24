package com.memes.api.service;

import java.util.List;

public record IndexResult(int indexed, long durationMs, List<String> errors) {}
