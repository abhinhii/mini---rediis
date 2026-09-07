package com.miniredis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class CliClient {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 6379;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println(in.readLine()); // welcome message from server

            // Background thread continuously prints anything the server sends,
            // so responses appear even if we're mid-typing the next command.
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("-> " + line);
                    }
                } catch (IOException e) {
                    // socket closed, normal on disconnect
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            System.out.println("mini-redis-cli connected. Type commands, or QUIT to exit.");
            while (true) {
                System.out.print("mini-redis> ");
                String command = scanner.nextLine();
                out.println(command);

                if (command.equalsIgnoreCase("QUIT") || command.equalsIgnoreCase("EXIT")) {
                    break;
                }
            }
        }
        System.out.println("Disconnected.");
    }
}
