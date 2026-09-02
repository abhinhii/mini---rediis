package com.miniredis.command;

public enum Command {
    SET, GET, DEL, EXPIRE, UNKNOWN;

    public static Command from(String token) {
        try {
            return Command.valueOf(token.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
