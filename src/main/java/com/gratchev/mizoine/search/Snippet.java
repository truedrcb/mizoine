package com.gratchev.mizoine.search;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A passage of a found file, as plain text in parts: the found words are the parts with {@code match}. */
@Schema(description = "A passage of the file; joined, the parts give the text; found words have match true")
public record Snippet(List<Part> parts) {

	public record Part(String text, boolean match) {
	}
}
