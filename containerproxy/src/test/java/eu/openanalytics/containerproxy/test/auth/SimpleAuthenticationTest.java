package eu.openanalytics.containerproxy.test.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@ActiveProfiles("test-simpleAuth")
public class SimpleAuthenticationTest {

	@Inject
	private MockMvc mvc;

	@Test
	public void authenticateUser() throws Exception {
		mvc
			.perform(get("/api/proxy").with(httpBasic("demo", "demo")).accept(MediaType.APPLICATION_JSON_VALUE))
			.andExpect(status().isOk());
	}
}
