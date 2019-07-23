package com.gratchev.mizoine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

// See https://open.sap.com/courses/cp5/items/1jX1dUlLoEMwUQ4uiJGAtx
// See https://www.baeldung.com/junit-5-migration
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {Application.class})
@WebAppConfiguration
public class IssueControllerWebTest {
	
	@Test
	public void testNothing() {
		
	}

}
