package com.al.lightq.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.config.SecurityConfig;
import com.al.lightq.model.Message;
import com.al.lightq.service.AcknowledgementService;
import com.al.lightq.service.DlqService;
import com.al.lightq.service.PopMessageService;
import com.al.lightq.service.PushMessageService;
import com.al.lightq.service.ViewMessageService;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
public class MessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PushMessageService pushMessageService;

	@MockBean
	private PopMessageService popMessageService;

	@MockBean
	private ViewMessageService viewMessageService;

	@MockBean
	private AcknowledgementService acknowledgementService;

	@MockBean
	private DlqService dlqService;

	@MockBean
	private LightQProperties lightQProperties; // Mock LightQProperties

	@BeforeEach
    void setUp() {
        when(lightQProperties.getMessageAllowedToFetch()).thenReturn(50); // Default value for tests
    }

	@Test
	@WithMockUser(username = "user", password = "password", roles = "USER")
	public void testPush() throws Exception {
		String jsonContent = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
		Message message = new Message("someId", "testGroup", jsonContent);
		when(pushMessageService.push(any(Message.class))).thenReturn(message);

		mockMvc.perform(post("/queue/push").header("consumerGroup", "testGroup").contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent).with(csrf())).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
	public void testPushAsAdmin() throws Exception {
		String jsonContent = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
		Message message = new Message("someId", "testGroup", jsonContent);
		when(pushMessageService.push(any(Message.class))).thenReturn(message);

		mockMvc.perform(post("/queue/push").header("consumerGroup", "testGroup").contentType(MediaType.APPLICATION_JSON)
				.content(jsonContent).with(csrf())).andExpect(status().isOk());
	}

	@Test
    @WithMockUser(username = "user", password = "password", roles = "USER")
    public void testPop() throws Exception {
        when(popMessageService.pop(anyString())).thenReturn(Optional.of(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/pop")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isOk());
    }

	@Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testPopAsAdmin() throws Exception {
        when(popMessageService.pop(anyString())).thenReturn(Optional.of(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/pop")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isOk());
    }

	@Test
    @WithMockUser(username = "user", password = "password", roles = "USER")
    public void testViewForbiddenForUser() throws Exception {
        // Mock the view service with the updated signature
        when(viewMessageService.view(anyString(), anyInt(), anyString())).thenReturn(Arrays.asList(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup")
                .header("messageCount", "10")) // Include messageCount header
                .andExpect(status().isForbidden());
    }

	@Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testViewAsAdminWithoutProcessedHeader() throws Exception {
        // Mock the view service with the updated signature
        when(viewMessageService.view(anyString(), anyInt(), anyString())).thenReturn(Arrays.asList(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup")
                .header("messageCount", "10")) // Include messageCount header
                .andExpect(status().isOk());
    }

	@Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testViewAsAdminWithProcessedYes() throws Exception {
        // Mock the view service with the updated signature
        when(viewMessageService.view(anyString(), anyInt(), anyString())).thenReturn(Arrays.asList(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup")
                .header("consumed", "yes")
                .header("messageCount", "10")) // Include messageCount header
                .andExpect(status().isOk());
    }

	@Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testViewAsAdminWithProcessedNo() throws Exception {
        // Mock the view service with the updated signature
        when(viewMessageService.view(anyString(), anyInt(), anyString())).thenReturn(Arrays.asList(new Message("someId", "testGroup", "Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup")
                .header("consumed", "no")
                .header("messageCount", "10")) // Include messageCount header
                .andExpect(status().isOk());
    }
}
