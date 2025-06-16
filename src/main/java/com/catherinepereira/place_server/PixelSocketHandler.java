package com.catherinepereira.place_server;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.annotation.JsonProperty;


public class PixelSocketHandler extends TextWebSocketHandler {
    private final int width;
    private final int height;
    private final String[][] board;

    public record PlaceMessage(@JsonProperty("x") int x, @JsonProperty("y") int y, @JsonProperty("color") String color) {}

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(PixelSocketHandler.class);

    public PixelSocketHandler(int width, int height) {
        this.board = new String[width][height];
        this.width = width;
        this.height = height;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        logger.debug("Client connected: {}", session.getId());
        sessions.add(session);

        // Replicate board to new client
        var objectMapper = new ObjectMapper();

        String[] batchedMessages = new String[width * height];

        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[x].length; y++) {
                String color = board[x][y];
                if (color == null) {
                    continue;
                }

                var placeMessage = new PlaceMessage(x, y, color);
                String json;
                try {
                    json = objectMapper.writeValueAsString(placeMessage);
                } catch (IOException e) {
                    logger.error(e.getMessage());
                    return;
                }
                batchedMessages[y * height + x] = json;
            }
        }

        String[] nonNullMessages = Arrays.stream(batchedMessages).filter(Objects::nonNull).toArray(String[]::new);
        logger.debug("Batched message: {} ", (Object) nonNullMessages);

        String message = Arrays.toString(nonNullMessages);
        session.sendMessage(new TextMessage(message));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        var objectMapper = new ObjectMapper();

        PlaceMessage placeMessage;
        try {
            placeMessage = objectMapper.readValue(payload, PlaceMessage.class);
        } catch (IOException e) {
            logger.warn("Client {} sent message in improper format, disconnecting.", session.getId(), e);
            session.close();
            return;
        }

        board[placeMessage.x][placeMessage.y] = placeMessage.color;

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                logger.debug("Client {} sent message: {}", session.getId(), message);
                s.sendMessage(message);
            }
        }
    }
}
