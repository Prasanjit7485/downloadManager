package com.threading.downloadmanager.controller;

import com.threading.downloadmanager.service.DownloaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/download")
public class DownloaderController
{
    @Autowired
    DownloaderService downloaderService;
    @GetMapping("/start")
    public ResponseEntity<String> download(@RequestParam String  url) throws IOException
    {
        downloaderService.startDownloading(url);
        return ResponseEntity.ok("Success");
    }
    @PutMapping("/pause")
    public ResponseEntity<String> pause(@RequestParam String  url) throws IOException
    {
        downloaderService.pauseDownload(url);
        return ResponseEntity.ok("Success");
    }

}
