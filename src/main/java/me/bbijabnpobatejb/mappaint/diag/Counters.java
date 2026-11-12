package me.bbijabnpobatejb.mappaint.diag;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Counters {

    public long mapPackets;
    public long mapBytes;
    public long displayPackets;
    public long strokePoints;

    public void map(int bytes) {
        mapPackets++;
        mapBytes += bytes;
    }
}
