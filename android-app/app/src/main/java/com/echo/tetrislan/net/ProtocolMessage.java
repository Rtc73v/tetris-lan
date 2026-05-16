package com.echo.tetrislan.net;

public final class ProtocolMessage {
    public static final String PROTOCOL_VERSION = "TG1";

    public static final String HELLO = "HELLO";
    public static final String STATE = "STATE";
    public static final String READY = "READY";
    public static final String CHAT = "CHAT";
    public static final String START = "START";
    public static final String GARBAGE = "GARBAGE";
    public static final String KO = "KO";
    public static final String LEAVE = "LEAVE";
    public static final String KICK = "KICK";
    public static final String DISBAND = "DISBAND";
    public static final String SURRENDER = "SURRENDER";
    public static final String RETURN_LOBBY = "RETURN_LOBBY";
    public static final String RECONNECT = "RECONNECT";
    public static final String BOT_STATE = "BOT_STATE";

    private ProtocolMessage() {}
}
