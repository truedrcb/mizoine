package com.gratchev.mizoine.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.repository.meta.CommentMeta;

@JsonInclude(Include.NON_NULL)
public class Comment extends BaseEntityInfo<CommentMeta> {
}
