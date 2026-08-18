package com.threading.downloadmanager.DTO;

import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.enums.DownloadStatus;
import com.threading.downloadmanager.service.DownloaderThread;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Data
public class DownloaderTaskDTO {
    private long id;
    @NonNull
    private String url;
    private String fileName;
    private Long downloadedSize;
    private Long fileSize;
    private DownloadStatus downloadStatus;
    private List<DownloadChunkDTO> downloadChunkListDTO;
}
