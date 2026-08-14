package com.threading.downloadmanager.controller;

import com.threading.downloadmanager.DTO.DownloaderTaskDTO;
import com.threading.downloadmanager.service.DownloaderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/download")
public class DownloaderController
{
    @Autowired
    DownloaderService downloaderService;
    @GetMapping("/start")
    public ResponseEntity<String> download(@Valid @RequestBody DownloaderTaskDTO downloaderTaskDTO) throws IOException
    {
        downloaderService.addDownloaderTask(downloaderTaskDTO);
        return ResponseEntity.ok("Success");
    }
    @PutMapping("/pause/{id}")
    public ResponseEntity<String> pause(@PathVariable Long id ) throws IOException
    {
        downloaderService.pauseDownload(id);
        return ResponseEntity.ok("Paused");
    }
    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<String> cancel(@PathVariable Long id) throws IOException
    {
        downloaderService.cancelDownload(id);
        return ResponseEntity.ok("Cancelled");
    }

}
