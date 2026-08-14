package com.threading.downloadmanager.repository;

import com.threading.downloadmanager.entity.DownloaderTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloaderTaskRepository extends JpaRepository<DownloaderTask, Long> {
}
