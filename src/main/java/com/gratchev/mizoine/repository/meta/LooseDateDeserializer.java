package com.gratchev.mizoine.repository.meta;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class LooseDateDeserializer extends JsonDeserializer<Date> {
	private final List<SimpleDateFormat> formatters = List.of(
		new SimpleDateFormat("dd-MM-yyyy HH:mm:ssZ"),
		new SimpleDateFormat("dd-MM-yyyy HH:mm:ss"),
		new SimpleDateFormat("dd-MM-yyyy HH:mm"),
		new SimpleDateFormat("dd-MM-yyyy"),
		new SimpleDateFormat("dd/MM/yyyy HH:mm:ssZ"),
		new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"),
		new SimpleDateFormat("dd/MM/yyyy HH:mm"),
		new SimpleDateFormat("dd/MM/yyyy"));

	@Override
	public Date deserialize(final JsonParser parser, final DeserializationContext c) throws IOException {
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
