package com.miniredis.command;

import com.miniredis.store.KeyValueStore;

public class CommandParser {

    private final KeyValueStore store;

    public CommandParser(KeyValueStore store) {
        this.store = store;
    }

    /**
     * Parses and executes a single raw command line, returning the
     * response string to send back to the client (without trailing newline).
     */
    public String execute(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return "-ERR empty command";
        }

        String[] tokens = rawLine.trim().split("\\s+");
        Command command = Command.from(tokens[0]);

        switch (command) {
            case SET -> {
                if (tokens.length < 3) {
                    return "-ERR wrong number of arguments for 'SET' (usage: SET key value [ttlSeconds])";
                }
                String key = tokens[1];
                String value = tokens[2];
                long ttl = 0;
                if (tokens.length >= 4) {
                    try {
                        ttl = Long.parseLong(tokens[3]);
                    } catch (NumberFormatException e) {
                        return "-ERR ttlSeconds must be an integer";
                    }
                }
                store.set(key, value, ttl);
                return "+OK";
            }
            case GET -> {
                if (tokens.length != 2) {
                    return "-ERR wrong number of arguments for 'GET' (usage: GET key)";
                }
                String result = store.get(tokens[1]);
                return (result == null) ? "(nil)" : result;
            }
            case DEL -> {
                if (tokens.length != 2) {
                    return "-ERR wrong number of arguments for 'DEL' (usage: DEL key)";
                }
                boolean existed = store.del(tokens[1]);
                return existed ? ":1" : ":0";
            }
            case EXPIRE -> {
                if (tokens.length != 3) {
                    return "-ERR wrong number of arguments for 'EXPIRE' (usage: EXPIRE key ttlSeconds)";
                }
                long ttl;
                try {
                    ttl = Long.parseLong(tokens[2]);
                } catch (NumberFormatException e) {
                    return "-ERR ttlSeconds must be an integer";
                }
                boolean updated = store.expire(tokens[1], ttl);
                return updated ? ":1" : ":0";
            }
            default -> {
                return "-ERR unknown command '" + tokens[0] + "'";
            }
        }
    }
}
