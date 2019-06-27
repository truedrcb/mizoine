package com.gratchev.mizoine.repository.meta;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class LooseDateDeserializer extends JsonDeserializer<Date> {
	private List<SimpleDateFormat> formatters = new ArrayList<>();

	public LooseDateDeserializer() {
		formatters.add(new SimpleDateFormat("dd-MM-yyyy HH:mm:ssZ"));
		formatters.add(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss"));
		formatters.add(new SimpleDateFormat("dd-MM-yyyy HH:mm"));
		formatters.add(new SimpleDateFormat("dd-MM-yyyy"));
		formatters.add(new SimpleDateFormat("dd/MM/yyyy HH:mm:ssZ"));
		formatters.add(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"));
		formatters.add(new SimpleDateFormat("dd/MM/yyyy HH:mm"));
		formatters.add(new SimpleDateFormat("dd/MM/yyyy"));
	}

	@Override
	public Date deserialize(final JsonParser parser, 
			final DeserializationContext ctxt) throws IOException, JsonProcessingException {
		final String text = parser.getText();
		for (final SimpleDateFormat formatter : formatters) {
			try {
				return formatter.parse(text);
			} catch (final ParseException pe) {
				// skip to next possible format
			}
		}
		throw new JsonParseException(parser, "Cannot parse date: " + text);
	}

}
