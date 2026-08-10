package com.threading.downloadmanager.entity;

import com.threading.downloadmanager.enums.DownloadStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

@Getter
@Setter

public class DownloadChunk
{
    private long start;
    private long end;
    private volatile DownloadStatus downloadStatus;
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    public void addDownloadedBytes(long bytes)
    {
        downloadedBytes.addAndGet(bytes);
    }

}
