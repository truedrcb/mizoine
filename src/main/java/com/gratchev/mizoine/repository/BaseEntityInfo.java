package com.gratchev.mizoine.repository;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.repository.meta.BaseMeta;

@JsonInclude(Include.NON_NULL)
public class  BaseEntityInfo<T extends BaseMeta> implements Comparable<BaseEntityInfo<T>> {
	public String id;
	public T meta;
	public Set<String> tags;
	public Set<String> status;
	
	@Override
	public int compareTo(BaseEntityInfo<T> o) {
		return this.id.compareTo(o.id);
	}
}
