package com.gratchev.mizoine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

// See https://open.sap.com/courses/cp5/items/1jX1dUlLoEMwUQ4uiJGAtx
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {Application.class})
@WebAppConfiguration
public class IssueControllerWebTest {
	
	@Test
	public void testNothing() {
		
	}

}
