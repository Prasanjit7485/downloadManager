package com.threading.downloadmanager.entity;
import jakarta.persistence.*;
import lombok.Data;
import com.threading.downloadmanager.enums.DownloadStatus;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "downloader_task")
public class DownloaderTask
{
    @Id
    @GeneratedValue
    private Long id;
    private String url;
    private String fileName;
    private Long downloadedSize;
    private Long fileSize;
    @Enumerated(EnumType.STRING)
    private DownloadStatus downloadStatus;
    @OneToMany(mappedBy = "downloaderTask",cascade=CascadeType.ALL,orphanRemoval = true)
    private List<DownloadChunk> downloadChunkList = new ArrayList<>();
}
