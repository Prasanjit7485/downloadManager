package com.threading.downloadmanager.entity;
import jakarta.persistence.*;
import lombok.Data;
import com.threading.downloadmanager.enums.DownloadStatus;
import java.util.List;

@Entity
@Data
public class DownloaderTask
{
    @Id
    @GeneratedValue
    private Long id;
    private String url;
    private String fileName;
    private Long downloadedSize;
    private Long fileSize;
    private DownloadStatus downloadStatus;
    @OneToMany(mappedBy="downloadChunk",fetch=FetchType.LAZY)
    private List<DownloadChunk> downloadChunkList;
}
