package com.al.simplequeueservice.controller;

import com.al.simplequeueservice.exception.ErrorResponse;
import com.al.simplequeueservice.model.Message;
import com.al.simplequeueservice.model.MessageResponse;
import com.al.simplequeueservice.service.PopMessageService;
import com.al.simplequeueservice.service.PushMessageService;
import com.al.simplequeueservice.service.ViewMessageService;
import com.al.simplequeueservice.util.SQSConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for handling message-related operations in the Simple Queue Service.
 * Provides endpoints for pushing, popping, and viewing messages within consumer groups.
 */
@RestController
@RequestMapping(SQSConstants.QUEUE_BASE_URL)
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private PushMessageService pushMessageService;

    @Autowired
    private PopMessageService popMessageService;

    @Autowired
    private ViewMessageService viewMessageService;

    @Value("${no.of.message.allowed.to.fetch}")
    private long messageAllowedCount;

    /**
     * Pushes a new message to the queue for a specific consumer group.
     *
     * @param consumerGroup The header indicating the consumer group for the message.
     * @param content       The content of the message to be pushed.
     * @return A {@link MessageResponse} containing details of the pushed message.
     */
    @PostMapping(SQSConstants.PUSH_URL)
    public MessageResponse push(
            @RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER)
            @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = "Invalid consumer group name.")
            String consumerGroup,

            @NotBlank(message = "Message content cannot be empty")
            @Size(max = 1048576, message = "Message size cannot exceed 1MB")
            @RequestBody String content
    ) {
        logger.debug("Received push request for consumer group: {} with content: {}", consumerGroup, content);
        String messageId = UUID.randomUUID().toString();
        Message message = new Message(messageId, consumerGroup, content);
        Message pushedMessage = pushMessageService.push(message);
        logger.info("Message with ID {} pushed to consumer group {}", pushedMessage.getId(), consumerGroup);
        return new MessageResponse(pushedMessage);
    }

    /**
     * Pops the oldest available message from the queue for a specific consumer group.
     *
     * @param consumerGroup The header indicating the consumer group from which to pop the message.
     * @return A {@link ResponseEntity} containing a {@link MessageResponse} if a message is found,
     * or a not found response if the queue is empty.
     */
    @GetMapping(SQSConstants.POP_URL)
    public ResponseEntity<MessageResponse> pop(@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup) {
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
    @GetMapping(SQSConstants.VIEW_URL)
    public ResponseEntity<?> view(@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup, @RequestHeader(value = SQSConstants.MESSAGE_COUNT_HEADER) int messageCount,
                                  @RequestHeader(value = SQSConstants.CONSUMED, required = false) String consumed) {
        logger.debug("Received view request for consumer group: {}, message count: {}, consumed status: {}", consumerGroup, messageCount, StringUtils.isEmpty(consumed) ? "N/A" : consumed);

        if (messageCount < 1 || messageCount > messageAllowedCount) {
            logger.warn("Invalid message count requested: {}. Allowed range: 1 to {}", messageCount, messageAllowedCount);
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", String.format(SQSConstants.MESSAGE_COUNT_VALIDATION_ERROR_MESSAGE, messageAllowedCount), SQSConstants.QUEUE_BASE_URL + SQSConstants.VIEW_URL));
        }

        List<Message> messages = viewMessageService.view(consumerGroup, messageCount, consumed);
        logger.info("Returning {} messages for consumer group: {}, filtered by consumed status: {}", messages.size(), consumerGroup, StringUtils.isEmpty(consumed) ? "N/A" : consumed);
        return ResponseEntity.ok(messages);
    }
}
