package eu.openanalytics.containerproxy.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SwaggerUIController {

	@RequestMapping("/api/doc")
	public String swaggerUI() {
		return "redirect:/swagger-ui.html";
	}

}
