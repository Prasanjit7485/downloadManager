package com.threading.downloadmanager.DTO;
import com.threading.downloadmanager.enums.DownloadStatus;
import lombok.Data;

@Data
public class DownloadChunkDTO
{
    private long id;
    private long start;
    private long end;
    private  DownloadStatus downloadStatus;
    private Long downloadedBytes;
}
