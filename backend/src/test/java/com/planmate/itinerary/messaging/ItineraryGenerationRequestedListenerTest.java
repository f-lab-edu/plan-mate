package com.planmate.itinerary.messaging;

import static org.mockito.Mockito.verify;

import com.planmate.itinerary.service.ItineraryGenerationWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationRequestedListenerTest {

    @Mock
    private ItineraryGenerationWorkerService workerService;

    @Test
    void passesRabbitRedeliveryMetadataToWorker() {
        ItineraryGenerationRequestedMessage payload =
                new ItineraryGenerationRequestedMessage(1L, 2L, 3L);
        MessageProperties properties = new MessageProperties();
        properties.setRedelivered(true);
        Message rabbitMessage = new Message(new byte[0], properties);

        new ItineraryGenerationRequestedListener(workerService).handle(payload, rabbitMessage);

        verify(workerService).process(payload, true);
    }
}
