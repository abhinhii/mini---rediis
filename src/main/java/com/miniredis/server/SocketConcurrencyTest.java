package com.miniredis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketConcurrencyTest {

    private static final int CLIENT_THREADS = 100;
    private static final int OPS_PER_CLIENT = 50;
    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CLIENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1); // all threads wait, then fire at once
        CountDownLatch doneLatch = new CountDownLatch(CLIENT_THREADS);

        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < CLIENT_THREADS; t++) {
            final int clientId = t;
            pool.submit(() -> {
                try {
                    startLatch.await(); // block until the starting gun fires

                    try (Socket socket = new Socket(HOST, PORT);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        socket.setTcpNoDelay(true); // disable Nagle's algorithm — ADD THIS LINE

                        in.readLine(); // consume welcome message

                        for (int i = 0; i < OPS_PER_CLIENT; i++) {
                            String key = "concurrent-key-" + (i % 20);
                            String value = "client" + clientId + "-val" + i;

                            out.println("SET " + key + " " + value + " 0");
                            String setResp = in.readLine();

                            out.println("GET " + key);
                            String getResp = in.readLine();

                            if ("+OK".equals(setResp) && getResp != null) {
                                successfulOps.incrementAndGet();
                            } else {
                                errors.incrementAndGet();
                            }
                        }

                        out.println("QUIT");
                        in.readLine();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    System.err.println("Client " + clientId + " error: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        System.out.println("Launching " + CLIENT_THREADS + " concurrent clients...");
        long start = System.currentTimeMillis();
        startLatch.countDown(); // release all threads simultaneously
        doneLatch.await(); // wait for all to finish
        long elapsed = System.currentTimeMillis() - start;

        pool.shutdown();

        System.out.println("=== Results ===");
        System.out.println("Total operations attempted: " + (CLIENT_THREADS * OPS_PER_CLIENT));
        System.out.println("Successful SET+GET pairs:   " + successfulOps.get());
        System.out.println("Errors:                     " + errors.get());
        System.out.println("Elapsed time:                " + elapsed + "ms");
        System.out.println(errors.get() == 0
                ? "PASS: No errors under concurrent socket load."
                : "FAIL: Errors detected, investigate above.");
    }
}
