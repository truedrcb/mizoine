package com.gratchev.mizoine.repository.meta;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class LooseDateSerializer extends JsonSerializer<Date> {
    private SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ssZ");

	@Override
	public void serialize(
			final Date value, 
			final JsonGenerator gen, 
			final SerializerProvider serializers) throws IOException {
		gen.writeString(formatter.format(value));
	}

}
