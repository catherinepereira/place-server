package com.catherinepereira.placeserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PixelSocketHandler extends BinaryWebSocketHandler {
    private final int width;
    private final int height;
    private final int paletteIndexLimit = 15;
    private final int payloadMaxLength = 5;
    private final int[][] board;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(PixelSocketHandler.class);

    public PixelSocketHandler(int width, int height) {
        this.board = new int[width][height];
        this.width = width;
        this.height = height;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.debug("Client connected: {}", session.getId());
        sessions.add(session);

//        byte[] packedArray = new byte[width * height / 2];
//
//        for (int x = 0; x < this.width; x++) {
//            for (int y = 0; y < this.height; y += 2) {
//                int currentColor = this.board[x][y];
//                var nextColor = this.board[x][y + 1];
//
///               // Handle case where board size is odd and last color byte in array is undefined
//
//                int currentPackedColor = currentColor << 4;
//                int nextPackedColor = currentPackedColor | nextColor;
//
//                byte[] byteArray = ByteBuffer.allocate(2).put((byte) currentPackedColor).put((byte) nextPackedColor).array();
//                packedArray.push(byteArray);
//
//            }
//        }

//        session.sendMessage();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws RuntimeException, IOException {
        if (message.getPayloadLength() > this.payloadMaxLength) {
            System.out.println("Client attempted to send message exceeding message limit");
            session.close();
            return;
        }

        var payload = message.getPayload();
        var duplicate = payload.duplicate();

        var x = duplicate.getShort(0); // Reads x coord at byte index 0
        var y = duplicate.getShort(2); // Reads y coord at byte index 2
        var color = duplicate.get(4); // Reads color at byte index 4

        // Check coords are within bounds
        if (x > this.width) {
            System.out.println("x is out of bounds");
            return;
        }
        if (y > this.height) {
            System.out.println("y is out of bounds");
            return;
        }
        if (color > this.paletteIndexLimit) {
            System.out.println("Color is out of bounds");
            return;
        }

        board[x][y] = color;

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                logger.debug("Client {} sent message: {}", session.getId(), message);
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
