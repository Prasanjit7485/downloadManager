package com.threading.downloadmanager.service;
import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

public class DownloaderThread implements Runnable {
    private DownloadChunk downloadChunk;
    private AtomicLong totalDownloaded;
    private final String link;
    private final String fileName;
    private final DownloaderTask downloaderTask;
    private volatile HttpURLConnection con;

    public DownloaderThread(String link, String fileName, DownloadChunk downloadChunk, AtomicLong totalDownloaded,DownloaderTask downloaderTask) {
        this.link = link;
        this.fileName = fileName;
        this.downloadChunk = downloadChunk;
        this.totalDownloaded = totalDownloaded;
        this.downloaderTask = downloaderTask;
    }
    @Override
    public void run()
    {
        try
        {
            URL url = new URL(link);
            con=(HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            long start=downloadChunk.getStart()+downloadChunk.getDownloadedBytes().get();
            long end=downloadChunk.getEnd();
            if(start>end) return;
            con.setRequestProperty("Range", "bytes=" + start + "-" + end);
            con.connect();
            int responseCode = con.getResponseCode();
            System.out.println(
                    Thread.currentThread().getName() +
                            " Range: " + start + "-" + end +
                            " Response: " + con.getResponseCode()
            );
            long total=end-start+1;
            long downloadedBytes=0;
            long s=System.currentTimeMillis();
            long bytes=end-start+1;
            try (InputStream in = con.getInputStream();
                 RandomAccessFile raf = new RandomAccessFile(fileName, "rw");) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    if(downloaderTask.getDownloadStatus()==DownloadStatus.PAUSED)
                    {
                        System.out.println(Thread.currentThread().getName() +"Paused");
                        pause();
                        break;
                    }
                    raf.write(buffer, 0, bytesRead);
                    downloadedBytes+=bytesRead;
                    downloadChunk.addDownloadedBytes(bytesRead);
                    totalDownloaded.addAndGet(bytesRead);
                }
            }
            if(downloadedBytes==total)
            {
                downloadChunk.setDownloadStatus(DownloadStatus.COMPLETED);
            }
            else if(downloadChunk.getDownloadStatus()!=DownloadStatus.PAUSED)
            {
                downloadChunk.setDownloadStatus(DownloadStatus.FAILED);
            }
            long e=System.currentTimeMillis();
            double speed=(total*1000.0)/(s-e+1);
            speed/=(1024.0);
            double size=((double)bytes/(1024.0*1024.0));
            System.out.println("Completed "+ Thread.currentThread().getName()+" Downloaded "+size);
            con.disconnect();
        }
        catch (IOException e)        {
            downloadChunk.setDownloadStatus(
                    DownloadStatus.FAILED
            );
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
