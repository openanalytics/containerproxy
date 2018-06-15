package eu.openanalytics.containerproxy.ui;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.NestedServletException;

import eu.openanalytics.containerproxy.api.BaseController;

@Controller
public class ErrorController extends BaseController implements org.springframework.boot.web.servlet.error.ErrorController {
	
	@RequestMapping("/error")
	public String handleError(ModelMap map, HttpServletRequest request, HttpServletResponse response) {
		String message = "";
		String stackTrace = "";
		Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
		
		if (exception instanceof NestedServletException && exception.getCause() instanceof Exception) {
			exception = (Exception) exception.getCause();
		}
		if (exception != null) {
			if (exception.getMessage() != null) message = exception.getMessage();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (PrintWriter writer = new PrintWriter(bos)) {
				exception.printStackTrace(writer);
			}
			stackTrace = bos.toString();
			stackTrace = stackTrace.replace(System.getProperty("line.separator"), "<br/>");
		}
		
		if (message == null || message.isEmpty()) message = "An unexpected server error occurred";
		
		map.put("message", message);
		map.put("stackTrace", stackTrace);
		map.put("status", response.getStatus());
		
		return "error";
	}

	@Override
	public String getErrorPath() {
		return "/error";
	}

}