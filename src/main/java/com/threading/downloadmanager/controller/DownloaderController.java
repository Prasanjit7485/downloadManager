package com.threading.downloadmanager.controller;

import com.threading.downloadmanager.DTO.DownloaderTaskDTO;
import com.threading.downloadmanager.service.DownloaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/download")
public class DownloaderController
{
    @Autowired
    DownloaderService downloaderService;
    @GetMapping("/all")
    public ResponseEntity<List<DownloaderTaskDTO>> getAllDownloaderTasks()
    {
        return ResponseEntity.ok(downloaderService.getAllDownloaderTask());
    }

    @PostMapping("/add")
    public ResponseEntity<String> download(@RequestParam String url) throws IOException
    {
        downloaderService.addDownloaderTask(url);
        return ResponseEntity.ok("Success");
    }
    @PostMapping("/start")
    public ResponseEntity<String> startDownload() throws IOException, ExecutionException, InterruptedException {
        downloaderService.startQueueDownloading();
        return ResponseEntity.ok("Success");
    }
    @PutMapping("/pause/{id}")
    public ResponseEntity<String> pause(@PathVariable Long id ) throws IOException
    {
        downloaderService.pauseDownload(id);
        return ResponseEntity.ok("Paused");
    }
    @PutMapping("/resume/{id}")
    public ResponseEntity<String> resumeDownload(@PathVariable Long id) throws IOException, ExecutionException, InterruptedException {
        downloaderService.resumeDownloader(id);
        return ResponseEntity.ok("Resumed");
    }
    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<String> cancel(@PathVariable Long id) throws IOException
    {
        downloaderService.cancelDownload(id);
        return ResponseEntity.ok("Cancelled");
    }

}
