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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class MessageControllerTest {

	private MockMvc mockMvc;

	@Mock
	private PushMessageService pushMessageService;

	@Mock
	private PopMessageService popMessageService;

	@Mock
	private ViewMessageService viewMessageService;

	@Mock
	private AcknowledgementService acknowledgementService;

	@Mock
	private DlqService dlqService;

	@Mock
	private LightQProperties lightQProperties; // Mock LightQProperties
	private MessageController controller;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(lightQProperties.getMessageAllowedToFetch()).thenReturn(50); // Default value for tests
		controller = new MessageController(pushMessageService, popMessageService, viewMessageService, lightQProperties,
				acknowledgementService, dlqService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
    public void testViewAsUserReturnsOkInStandaloneSetup() throws Exception {
        // Mock the view service with the updated signature
        when(viewMessageService.view(anyString(), anyInt(), anyString())).thenReturn(Arrays.asList(new Message("someId", "testGroup", "Test message")));

        // With standalone MockMvc (no SecurityConfig applied), controller returns 200
        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup")
                .header("messageCount", "10")) // Include messageCount header
                .andExpect(status().isOk());
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
