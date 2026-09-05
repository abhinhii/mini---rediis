package com.miniredis.server;

import com.miniredis.command.CommandParser;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServer {

    private static final int THREAD_POOL_SIZE = 100;

    private final int port;
    private final CommandParser commandParser;
    private final ExecutorService clientPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public SocketServer(int port, CommandParser commandParser) {
        this.port = port;
        this.commandParser = commandParser;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SocketServer] Listening on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept(); // blocks until a client connects
                clientPool.submit(new ClientHandler(clientSocket, commandParser));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SocketServer] Error accepting connection: " + e.getMessage());
                }
                // if !running, this exception is expected (from stop() closing the socket)
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // unblocks the accept() call in start()
            }
        } catch (IOException e) {
            System.err.println("[SocketServer] Error closing server socket: " + e.getMessage());
        }
        clientPool.shutdown();
        System.out.println("[SocketServer] Stopped.");
    }
}
