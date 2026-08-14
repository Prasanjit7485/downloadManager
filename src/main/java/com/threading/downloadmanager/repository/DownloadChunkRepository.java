package com.threading.downloadmanager.repository;

import com.threading.downloadmanager.entity.DownloadChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadChunkRepository extends JpaRepository<DownloadChunk, Long> {
}
