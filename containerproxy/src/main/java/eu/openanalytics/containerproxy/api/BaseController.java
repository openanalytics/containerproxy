package eu.openanalytics.containerproxy.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

public class BaseController {

	public static class NotFoundException extends RuntimeException {
		
		private static final long serialVersionUID = 2042632906716154791L;

		public NotFoundException(String message) {
			super(message);
		}

	}
	
	@ControllerAdvice
	public static class RestErrorHandler {
	    @ExceptionHandler(NotFoundException.class)
	    @ResponseStatus(HttpStatus.NOT_FOUND)
	    @ResponseBody
	    public Object notFound(NotFoundException ex) {
	        return ex.getMessage();
	    }
	}
}
