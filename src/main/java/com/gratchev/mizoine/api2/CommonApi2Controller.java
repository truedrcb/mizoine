package com.gratchev.mizoine.api2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api2")
public class CommonApi2Controller {
	private static final Logger log = LoggerFactory.getLogger(CommonApi2Controller.class);
	
	@Value("${config:mizoine-config.json}")
	protected String configPath;

	public static class AppInfo {
		public String name = "pwn";
		public String config;
	}

	@GetMapping("/app")
	@ResponseBody
	public AppInfo getAppInfo() {
		final AppInfo appInfo = new AppInfo();
		appInfo.config = configPath;
		return appInfo;
	}

}
