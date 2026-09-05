package com.miniredis.server;

import com.miniredis.command.CommandParser;
import com.miniredis.persistence.AofLogger;
import com.miniredis.persistence.AofRecovery;
import com.miniredis.store.EvictionScheduler;
import com.miniredis.store.KeyValueStore;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        AofLogger aofLogger = new AofLogger("data/mini-redis.aof");
        KeyValueStore store = new KeyValueStore(aofLogger);

        AofRecovery.recover(store, aofLogger.getLogPath());

        EvictionScheduler evictionScheduler = new EvictionScheduler(store);
        evictionScheduler.start();

        CommandParser commandParser = new CommandParser(store);
        SocketServer server = new SocketServer(6379, commandParser);

        // Graceful shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received.");
            server.stop();
            evictionScheduler.shutdown();
        }));

        server.start(); // blocks here until server.stop() is called
    }
}
