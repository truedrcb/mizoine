package com.gratchev.mizoine.api;

import com.vladsch.flexmark.util.ast.Node;

public abstract class BaseDescriptionController extends BaseController {
	public static class DescriptionResponse {
		@Override
		public String toString() {
			return "DescriptionResponse [markdown=" + markdown + ", html=" + html + "]";
		}
		public String markdown;
		public String html;
	}

	protected DescriptionResponse descriptionResponse(final String description, final Node descriptionDocument) {
		final DescriptionResponse response = new DescriptionResponse();
		response.markdown = description;
		response.html = render(descriptionDocument);
		return response;
	}
}
