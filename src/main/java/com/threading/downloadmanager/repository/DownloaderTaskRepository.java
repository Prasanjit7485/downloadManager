package com.threading.downloadmanager.repository;

import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DownloaderTaskRepository extends JpaRepository<DownloaderTask, Long> {
    List<DownloaderTask> findAllByDownloadStatus(DownloadStatus status);
}

