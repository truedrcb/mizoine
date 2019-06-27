package com.gratchev.mizoine.repository.meta;

import java.util.Date;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class BaseMeta {

	public String title;
	
	// https://stackoverflow.com/questions/7556851/set-jackson-timezone-for-date-deserialization
	//@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ssZ", timezone = "CET")
	// See custom date serializer http://www.baeldung.com/jackson-serialize-dates
	@JsonSerialize(using = LooseDateSerializer.class)
	@JsonDeserialize(using = LooseDateDeserializer.class)
	public Date creationDate;

	public String creator;
	
	public BaseMeta() {
		super();
	}

	@Override
	public String toString() {
		return "BaseMeta [title=" + title + ", creationDate=" + creationDate + ", creator=" + creator + "]";
	}

}