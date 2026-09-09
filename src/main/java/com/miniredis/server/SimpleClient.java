package com.miniredis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SimpleClient {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 6379);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Server says: " + in.readLine()); // welcome message

            sendAndPrint(out, in, "SET user:1 Alice 0");
            sendAndPrint(out, in, "GET user:1");
            sendAndPrint(out, in, "DEL user:1");
            sendAndPrint(out, in, "GET user:1");
            sendAndPrint(out, in, "QUIT");
        }
    }

    private static void sendAndPrint(PrintWriter out, BufferedReader in, String command) throws IOException {
        out.println(command);
        System.out.println(command + "  ->  " + in.readLine());
    }
}
