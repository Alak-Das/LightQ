package com.al.lightq.controller;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import com.al.lightq.model.MessageResponse;
import com.al.lightq.service.PopMessageService;
import com.al.lightq.service.PushMessageService;
import com.al.lightq.service.ViewMessageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.al.lightq.util.LightQConstants.*;

/**
 * REST controller for handling message-related operations in the Simple Queue Service.
 * <p>
 * Provides endpoints for pushing, popping, and viewing messages within consumer groups.
 * </p>
 */
@RestController
@RequestMapping(QUEUE_BASE_URL)
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    private final PushMessageService pushMessageService;
    private final PopMessageService popMessageService;
    private final ViewMessageService viewMessageService;
    private final LightQProperties lightQProperties;

    public MessageController(PushMessageService pushMessageService, PopMessageService popMessageService, ViewMessageService viewMessageService, LightQProperties lightQProperties) {
        this.pushMessageService = pushMessageService;
        this.popMessageService = popMessageService;
        this.viewMessageService = viewMessageService;
        this.lightQProperties = lightQProperties;
    }

    /**
     * Pushes a new message to the queue for a specific consumer group.
     *
     * @param consumerGroup The header indicating the consumer group for the message.
     * @param content       The content of the message to be pushed.
     * @return A {@link MessageResponse} containing details of the pushed message.
     */
    @PostMapping(PUSH_URL)
    public MessageResponse push(
            @RequestHeader(CONSUMER_GROUP_HEADER)
            @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = INVALID_CONSUMER_GROUP_MESSAGE)
            String consumerGroup,

            @NotBlank(message = EMPTY_MESSAGE_CONTENT_MESSAGE)
            @Size(max = 1048576, message = MESSAGE_SIZE_EXCEEDED_MESSAGE)
            @RequestBody String content
    ) {
        int contentLength = content != null ? content.length() : 0;
        logger.debug("Received push request for consumer group: {} with contentLength={} chars", consumerGroup, contentLength);
        Message message = new Message(UUID.randomUUID().toString(), consumerGroup, content);
        Message pushedMessage = pushMessageService.push(message);
        logger.info("Message with ID {} pushed to consumer group {}", pushedMessage.getId(), consumerGroup);
        return new MessageResponse(pushedMessage);
    }

    /**
     * Pops the oldest available message for a specific consumer group.
     *
     * @param consumerGroup The header indicating the consumer group from which to pop the message.
     * @return A {@link ResponseEntity} containing a {@link MessageResponse} if a message is found,
     * or a not found response if the queue is empty.
     */
    @GetMapping(POP_URL)
    public ResponseEntity<MessageResponse> pop(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup) {
        logger.debug("Received pop request for consumer group: {}", consumerGroup);
        Optional<Message> message = popMessageService.pop(consumerGroup);
        if (message.isPresent()) {
            logger.info("Message with ID {} popped from consumer group {}", message.get().getId(), consumerGroup);
        } else {
            logger.info("No message found to pop for consumer group {}", consumerGroup);
        }
        return message.map(msg -> ResponseEntity.ok(new MessageResponse(msg)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Views messages in the queue for a specific consumer group, with optional filtering by consumption status.
     *
     * @param consumerGroup The header indicating the consumer group to view messages from.
     * @param messageCount  The maximum number of messages to retrieve.
     * @param consumed      Optional header to filter messages by consumption status ("yes" for consumed, "no" for unconsumed).
     * @return A {@link ResponseEntity} containing a list of {@link Message} objects.
     */
    @GetMapping(VIEW_URL)
    public ResponseEntity<?> view(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
                                  @RequestHeader(value = MESSAGE_COUNT_HEADER, required = false) Integer messageCount,
                                  @RequestHeader(value = CONSUMED, required = false) String consumed) {
        logger.debug("Received view request for consumer group: {}, message count: {}, consumed status: {}", consumerGroup, messageCount, StringUtils.isEmpty(consumed) ? NA : consumed);
        if (StringUtils.isNotEmpty(consumed) && !YES.equalsIgnoreCase(consumed) && !NO.equalsIgnoreCase(consumed)) {
            logger.warn(INVALID_CONSUMED_FILTER_MESSAGE, consumed);
        }

        int limit = (messageCount != null && messageCount > 0) ? Math.min(messageCount, lightQProperties.getMessageAllowedToFetch()) : lightQProperties.getMessageAllowedToFetch();

        List<Message> messages = viewMessageService.view(consumerGroup, limit, consumed);
        logger.info("Returning {} messages for consumer group: {}, filtered by consumed status: {}", messages.size(), consumerGroup, StringUtils.isEmpty(consumed) ? NA : consumed);
        return ResponseEntity.ok(messages);
    }
}
