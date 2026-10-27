package cn.iocoder.yudao.server.integration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Test-only SMTP endpoint: loopback bind, accepts locally, never resolves or relays recipients. */
final class JssLocalSmtpSink implements AutoCloseable {
    record Mail(List<String> recipients, String mime) {}

    private final ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
    final BlockingQueue<Mail> received = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean closed;
    private volatile Socket active;
    volatile boolean rejectDelivery;
    volatile int transactions;

    JssLocalSmtpSink() throws IOException {
        server.setSoTimeout(500);
        worker = new Thread(this::run, "jss-owned-smtp-sink");
        worker.setDaemon(true);
        worker.start();
    }

    int port() {
        return server.getLocalPort();
    }

    private void run() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                active = socket;
                socket.setSoTimeout(10000);
                serve(socket);
            } catch (SocketTimeoutException ignored) {
                // Poll only this owned listener; close() wakes both accept and an active client.
            } catch (IOException ignored) {
                // The test asserts receipt and persisted delivery status, rather than protocol
                // logs.
            } finally {
                active = null;
            }
        }
    }

    private void serve(Socket socket) throws IOException {
        var reader =
                new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.ISO_8859_1));
        var writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.US_ASCII));
        reply(writer, "220 localhost JSS isolated SMTP");
        List<String> recipients = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String command = line.toUpperCase(Locale.ROOT);
            if (command.startsWith("EHLO")) reply(writer, "250-localhost\r\n250 AUTH PLAIN LOGIN");
            else if (command.startsWith("HELO")) reply(writer, "250 localhost");
            else if (command.startsWith("AUTH LOGIN")) {
                if (command.equals("AUTH LOGIN")) {
                    reply(writer, "334 VXNlcm5hbWU6");
                    reader.readLine();
                }
                reply(writer, "334 UGFzc3dvcmQ6");
                reader.readLine();
                reply(writer, "235 authenticated");
            } else if (command.startsWith("AUTH PLAIN")) {
                if (command.equals("AUTH PLAIN")) {
                    reply(writer, "334 ");
                    reader.readLine();
                }
                reply(writer, "235 authenticated");
            } else if (command.startsWith("MAIL FROM:")) {
                recipients.clear();
                reply(writer, "250 accepted");
            } else if (command.startsWith("RCPT TO:")) {
                recipients.add(line.substring(8).trim());
                reply(writer, "250 accepted");
            } else if (command.equals("DATA")) {
                transactions++;
                reply(writer, "354 send message");
                StringBuilder mime = new StringBuilder();
                while ((line = reader.readLine()) != null && !line.equals("."))
                    mime.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                if (rejectDelivery) reply(writer, "550 isolated fixture rejection");
                else {
                    received.add(new Mail(List.copyOf(recipients), mime.toString()));
                    reply(writer, "250 queued locally");
                }
            } else if (command.equals("QUIT")) {
                reply(writer, "221 bye");
                return;
            } else if (command.equals("RSET")) {
                recipients.clear();
                reply(writer, "250 reset");
            } else reply(writer, "250 ok");
        }
    }

    private void reply(BufferedWriter writer, String text) throws IOException {
        writer.write(text + "\r\n");
        writer.flush();
    }

    @Override
    public void close() throws Exception {
        closed = true;
        server.close();
        Socket socket = active;
        if (socket != null) socket.close();
        worker.join(5000);
        if (worker.isAlive())
            throw new IllegalStateException("Owned SMTP worker did not terminate");
    }
}
