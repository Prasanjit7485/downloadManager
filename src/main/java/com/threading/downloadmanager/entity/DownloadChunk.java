package com.threading.downloadmanager.entity;

import com.threading.downloadmanager.enums.DownloadStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class DownloadChunk
{
    @Id
    @GeneratedValue
    private Long id;
    private Long start;
    private Long end;
    private  DownloadStatus downloadStatus;
    private Long downloadedBytes;
    @ManyToOne( fetch = FetchType.LAZY)
    @JoinColumn(name = "downloaderTask_id", nullable = false)
    private DownloaderTask downloaderTask;
}
