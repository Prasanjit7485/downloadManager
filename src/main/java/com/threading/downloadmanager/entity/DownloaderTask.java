package com.threading.downloadmanager.entity;
import lombok.Getter;
import lombok.Setter;
import com.threading.downloadmanager.enums.DownloadStatus;

@Setter
@Getter
public class DownloaderTask
{
    private String url;
    private String fileName;
    private Long downloadedSize;
    private Long fileSize;
    private volatile DownloadStatus downloadStatus;
}
