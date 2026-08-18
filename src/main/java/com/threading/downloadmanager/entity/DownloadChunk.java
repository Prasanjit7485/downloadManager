package com.threading.downloadmanager.entity;

import com.threading.downloadmanager.enums.DownloadStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "download_chunk")
public class DownloadChunk
{
    @Id
    @GeneratedValue
    private Long id;
    private Long startByte;
    private Long endByte;
    @Enumerated(EnumType.STRING)
    private  DownloadStatus downloadStatus;
    private Long downloadedBytes;
    @ManyToOne( fetch = FetchType.LAZY)
    @JoinColumn(name = "downloaderTask_id", nullable = false)
    private DownloaderTask downloaderTask;
}
