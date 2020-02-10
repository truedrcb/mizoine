package com.gratchev.mizoine.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Attribute;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;


public class RepositoryIndexerAutoCloseableTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexer.class);
	
	public static class CloseableTest implements AutoCloseable {

		@Override
		public void close() throws IOException {
		}
	}
	
	@Test
	public void autoCloseableTest() {
		LOGGER.info("Start");
		
		try (final CloseableTest ac1 = new CloseableTest() {
			{
				LOGGER.info("Creating normal autoCloseable");
			}
			
			@Override
			public void close() throws IOException {
				LOGGER.info("Closing normal autoCloseable");
			}
		}) {
			LOGGER.info("Test code before closing");
		} catch (IOException e) {
			LOGGER.info("Unexpected");
			fail("Unexpected");
		}
		LOGGER.info("End");
	}

	@Test
	public void autoCloseableFailTest() {
		LOGGER.info("Start");
		
		try (final CloseableTest ac1 = new CloseableTest() {
			{
				LOGGER.info("Creating autoCloseable fail sample");
				if ( "a".contains("a") ) {
					throw new IOException("Test fail");
				}
				LOGGER.info("Unexpected");
				fail("Unexpected");
			}
			
			@Override
			public void close() throws IOException {
				LOGGER.info("Closing failed autoCloseable");
				fail("Unexpected closing");
			}
		}) {
			
			LOGGER.info("Unexpected");
			fail("Unexpected");
			
		} catch (IOException e) {
			LOGGER.info("Expected exception");
		}
		LOGGER.info("End");
	}
	
	
	@Test
	public void analyzerParseString() throws IOException {
		final Set<String> tokens = new HashSet<>();
		
		try(final Analyzer analyzer = new StandardAnalyzer()) {
			try (final TokenStream ts = 
					analyzer.tokenStream("none", 
							"    Curabitur.ullamcorper ultricies-nisi. \"Nam eget_dui.\" [Etiam rhoncus](issue-WEB-3).")) {
				ts.reset();
				for (int i = 0; i < 1000; i++) {
					LOGGER.info("Token: " + ts);
					final Iterator<Class<? extends Attribute>> iterator = ts.getAttributeClassesIterator();
					for(;iterator.hasNext();) {
						final Class<? extends Attribute> a = iterator.next();
						LOGGER.info(" Attribute class: " + a);
					}
					final CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);
					LOGGER.info(" Token term: " + term);
					if (term.length() > 0) {
						tokens.add(term.toString());
					}
					
					if (!ts.incrementToken()) {
						break;
					}
				}
				ts.end();
			}
		}
		
		assertEquals(Sets.newHashSet("curabitur.ullamcorper", "ultricies", "nisi",
				"nam", "eget_dui", "etiam", "rhoncus", "issue", "web", "3"), tokens);
		


	}
}
