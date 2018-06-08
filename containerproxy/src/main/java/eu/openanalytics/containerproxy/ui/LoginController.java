package eu.openanalytics.containerproxy.ui;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import eu.openanalytics.containerproxy.api.BaseController;

@Controller
public class LoginController extends BaseController {

	@RequestMapping(value = "/login", method = RequestMethod.GET)
    public String getLoginPage(@RequestParam Optional<String> error, ModelMap map, HttpServletRequest request) {
		if (error.isPresent()) map.put("error", "Invalid user name or password");
        return "login";
    }

}