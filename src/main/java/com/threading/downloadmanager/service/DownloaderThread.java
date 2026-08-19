package com.threading.downloadmanager.service;
import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import com.threading.downloadmanager.repository.DownloadChunkRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

public class DownloaderThread implements Runnable {
    private final DownloadChunk downloadChunk;
    private final String link;
    private final String fileName;
    private final DownloaderTask downloaderTask;
    private final AtomicLong downloadBytes;
    private volatile HttpURLConnection con;
    private final DownloadChunkRepository downloadChunkRepository;
    public DownloaderThread(String link, String fileName, DownloadChunk downloadChunk, DownloaderTask downloaderTask, AtomicLong downloadBytes, DownloadChunkRepository downloadChunkRepository) {
        this.link = link;
        this.fileName = fileName;
        this.downloadChunk = downloadChunk;
        this.downloaderTask = downloaderTask;
        this.downloadBytes = downloadBytes;
        this.downloadChunkRepository = downloadChunkRepository;
    }
    @Override
    public void run()
    {
        try
        {
            URL url = new URL(link);
            con=(HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            long start=downloadChunk.getStartByte()+downloadChunk.getDownloadedBytes();
            long end=downloadChunk.getEndByte();
            if(start>end) return;
            con.setRequestProperty("User-Agent",
                    "Mozilla/5.0");
            con.setRequestProperty("Range", "bytes=" + start + "-" + end);
            con.connect();
            System.out.println(3);
            int responseCode = con.getResponseCode();
            System.out.println(
                    Thread.currentThread().getName() +
                            " Range: " + start + "-" + end +
                            " Response: " + responseCode
            );
            long total=end-start+1;
            long downloadedBytes=0;
            long s=System.currentTimeMillis();
            long bytes=end-start+1;
            try (InputStream in = con.getInputStream();
                 RandomAccessFile raf = new RandomAccessFile(fileName, "rw")) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    if(downloaderTask.getDownloadStatus()==DownloadStatus.PAUSED)
                    {
                        downloadChunk.setDownloadedBytes(downloadedBytes);
                        downloadChunk.setDownloadStatus(DownloadStatus.PAUSED);
                        downloadChunkRepository.save(downloadChunk);
                        System.out.println(Thread.currentThread().getName() +"Paused");
                        pause();
                        break;
                    }
                    raf.write(buffer, 0, bytesRead);
                    downloadedBytes+=bytesRead;
                    downloadBytes.addAndGet(bytesRead);
                }
                downloadChunk.setDownloadedBytes(downloadedBytes+downloadChunk.getDownloadedBytes());
            }
            if(downloadedBytes==total)
            {
                downloadChunk.setDownloadStatus(DownloadStatus.COMPLETED);
            }
            else if(downloaderTask.getDownloadStatus()==DownloadStatus.PAUSED)
            {
                downloadChunk.setDownloadStatus(DownloadStatus.PAUSED);
            }
            else
            {
                downloadChunk.setDownloadStatus(DownloadStatus.FAILED);
            }
            long e=System.currentTimeMillis();
            double speed=(total*1000.0)/(e-s+1);
            speed/=(1024.0);
            double size=((double)bytes/(1024.0*1024.0));
            System.out.println("Completed "+ Thread.currentThread().getName()+" Downloaded "+size);
            downloadChunkRepository.save(downloadChunk);
            con.disconnect();
        }
        catch (IOException e)
        {
            System.out.println(e+"Failed to download");
            downloadChunk.setDownloadStatus(
                    DownloadStatus.FAILED
            );
            downloadChunkRepository.save(downloadChunk);
            throw new RuntimeException(e);
        }
    }
    public void pause()
    {
        if(con!=null)
        {
            con.disconnect();
        }
    }
}
