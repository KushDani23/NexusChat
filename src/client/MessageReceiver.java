package client;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Runs on a background thread and continuously prints messages received from the server.
 * Stops automatically when the server closes the connection.
 */
public class MessageReceiver implements Runnable {

    private final BufferedReader in;

    public MessageReceiver(BufferedReader in) {
        this.in = in;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            // Socket closed after /quit — expected, not an error
        }
    }
}
