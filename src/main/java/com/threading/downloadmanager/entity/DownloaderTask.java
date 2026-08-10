package com.threading.downloadmanager.entity;
import com.threading.downloadmanager.service.DownloaderThread;
import lombok.Getter;
import lombok.Setter;
import com.threading.downloadmanager.enums.DownloadStatus;

import java.util.List;

@Setter
@Getter
public class DownloaderTask
{
    private String url;
    private String fileName;
    private Long downloadedSize;
    private Long fileSize;
    private volatile DownloadStatus downloadStatus;
    private List<DownloaderThread> downloaderThreads;
}
