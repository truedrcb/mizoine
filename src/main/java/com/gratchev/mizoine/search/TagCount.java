package com.gratchev.mizoine.search;

import io.swagger.v3.oas.annotations.media.Schema;

/** A tag in use (step 65), in lower case, and on how many folders the user may read. */
@Schema(description = "A tag in use, in lower case, and on how many folders")
public record TagCount(String tag, int count) {
}
