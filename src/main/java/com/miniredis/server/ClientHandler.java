package com.miniredis.server;

import com.miniredis.command.CommandParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final CommandParser commandParser;

    public ClientHandler(Socket clientSocket, CommandParser commandParser) {
        this.clientSocket = clientSocket;
        this.commandParser = commandParser;
        try {
            clientSocket.setTcpNoDelay(true); // disable Nagle's algorithm
        } catch (IOException e) {
            System.err.println("Failed to set TCP_NODELAY: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();
        System.out.println("[ClientHandler] Connected: " + clientAddress);

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            out.println("Connected to Mini-Redis. Type commands (SET/GET/DEL/EXPIRE), or QUIT to disconnect.");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.equalsIgnoreCase("QUIT") || line.equalsIgnoreCase("EXIT")) {
                    out.println("+OK bye");
                    break;
                }

                String response = commandParser.execute(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("[ClientHandler] Connection error for " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[ClientHandler] Disconnected: " + clientAddress);
        }
    }
}
