package com.mobiled.android.base.comman;

import com.mobiled.android.LogSystem;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;

/**
 * Singleton class managing a socket connection.
 */
public class LogSocketManager {

    // Single instance of this manager.
    private static LogSocketManager manager;
    // Constants
    private final String TAG = "SocketManager";
    /**
     * Initiates the socket connection.
     * Notifies the provided callback of the connection state.
     *
     * @param socketCallback Callback to receive connection state updates.
     */
    public LogSocketState socketState = LogSocketState.NONE;
    // Thread pool executor
    ExecutorService executor = Executors.newSingleThreadExecutor();
    String previousDeviceTag = "";
    private Socket mSocket;

    private AtomicBoolean connectAttempted = new AtomicBoolean(false);

    /**
     * Private constructor for singleton pattern.
     */
    private LogSocketManager() {
    }

    /**
     * Returns the single instance of this manager.
     * If it does not exist, it is created.
     *
     * @return The single instance of this manager.
     */
    public static LogSocketManager getSocketManager() {
        if (manager == null) {
            manager = new LogSocketManager();
        }
        return manager;
    }

    public void connect(LogSocketCallback _socketCallback) {
        LogSystem.e(TAG, "LogSocket Connection Attempted");
        AtomicReference<LogSocketCallback> socketCallback = new AtomicReference<>(_socketCallback);
        //Check Previoursly Attempted
        if (socketState != LogSocketState.NONE) {
            if (socketState == LogSocketState.LOADING) {
                return;
            } else if (socketState == LogSocketState.CONNECTED && mSocket != null && mSocket.connected()) {
                if (socketCallback.get() != null) {
                    socketCallback.get().onStateChanged(socketState);
                    socketCallback.set(null);
                }
                return;
            }
        }

        connectAttempted.set(true);
        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{Polling.NAME, WebSocket.NAME};
            mSocket = IO.socket("http://173.249.36.203:3000", options);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mSocket.on(Socket.EVENT_CONNECT, args -> {
            if (socketCallback.get() != null) socketCallback.get().onStateChanged(LogSocketState.CONNECTED);
            socketCallback.set(null);
        });
        mSocket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            if (socketCallback.get() != null) socketCallback.get().onStateChanged(LogSocketState.ERROR);
            socketCallback.set(null);
        });

        if (mSocket != null) {
            if (socketCallback.get() != null) socketCallback.get().onStateChanged(LogSocketState.LOADING);
            mSocket.connect();
        } else {
            if (socketCallback.get() != null) socketCallback.get().onStateChanged(LogSocketState.ERROR);
        }
    }

    public void writeLog(String message) {
        LogSystem.e(TAG, "[" + message + "]");
        JSONObject params = new JSONObject();
        try {
            params.put("message", message);
            if (mSocket.connected()) {
                mSocket.emit("AppLog", params);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeByteProcessLog(@NotNull String formattedHex) {
        JSONObject params = new JSONObject();
        try {
            params.put("message", formattedHex);
            if (mSocket.connected()) {
                mSocket.emit("ByteProcess", params);
            }
        } catch (Exception e) {
        }
    }

    public enum LogSocketState {
        NONE, LOADING, CONNECTED, ERROR
    }

    public interface LogSocketCallback {
        /**
         * Called when the state of the connection has changed.
         *
         * @param state The new state of the connection.
         */
        void onStateChanged(LogSocketState state);
    }

}
