package com.gratchev.mizoine.search;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/** Search is on unless {@code mizoine.search.enabled} is {@code false}: the one place this is decided. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty(name = "mizoine.search.enabled", matchIfMissing = true)
@interface SearchEnabled {
}
