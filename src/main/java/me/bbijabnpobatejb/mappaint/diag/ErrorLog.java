package me.bbijabnpobatejb.mappaint.diag;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ErrorLog extends Handler {

    private static final int LIMIT = 50;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    Deque<String> entries = new ArrayDeque<>();

    @Override
    public synchronized void publish(LogRecord record) {
        if (record.getLevel().intValue() < Level.WARNING.intValue()) return;

        val text = new StringWriter();
        text.append(LocalDateTime.now().format(TIME)).append(" [").append(record.getLevel().getName()).append("] ")
                .append(String.valueOf(record.getMessage())).append('\n');
        if (record.getThrown() != null) record.getThrown().printStackTrace(new PrintWriter(text, true));

        entries.addLast(text.toString());
        while (entries.size() > LIMIT) entries.removeFirst();
    }

    public synchronized List<String> entries() {
        return new ArrayList<>(entries);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
