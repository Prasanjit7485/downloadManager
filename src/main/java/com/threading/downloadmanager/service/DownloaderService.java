package com.threading.downloadmanager.service;

import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DownloaderService
{
    ConcurrentHashMap<String, DownloaderTask> activeDownloads = new ConcurrentHashMap<>();
    public void startDownloading(String link) throws IOException
    {
        URL url=new URL(link);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        DownloaderTask task;
        long downloadedBytes =0;
        if(activeDownloads.containsKey(link))
        {
            task=activeDownloads.get(link);
            downloadedBytes =task.getDownloadedSize();
            if(task.getDownloadStatus()==DownloadStatus.COMPLETED) return;
            task.setDownloadStatus(DownloadStatus.RESUMED);
        }
        else
        {
            task=new DownloaderTask();
            task.setUrl(link);
            task.setDownloadStatus(DownloadStatus.RESUMED);
            activeDownloads.put(link,task);
        }
        long totalSize=con.getContentLength();
        double total=totalSize/(1024.0*1024.0);
        task.setFileSize(totalSize);
        String disposition=con.getHeaderField("Content-Disposition");
        String fileName="";
        if(disposition!=null&&disposition.contains("filename="))
        {
            fileName = disposition.substring(
                    disposition.indexOf("filename=") + 9
            ).replace("\"", "");
        }
        if(fileName.isEmpty())
        {
            fileName="download"+System.currentTimeMillis() + ".bin";
        }
        File file=new File(fileName);
        if(file.exists())
        {
            if(task.getFileName()==null) fileName=fileName.substring(0,fileName.indexOf('.'))+ System.currentTimeMillis()+fileName.substring(fileName.indexOf('.'));
            else fileName=task.getFileName();
        }
        System.out.println("Downloading "+fileName);
        System.out.println("Total Bytes "+totalSize);
        AtomicLong totalDownloaded = new AtomicLong(0);
        int numberOfThread=4;
        ExecutorService executor= Executors.newFixedThreadPool(numberOfThread);
        long chunks=totalSize/numberOfThread;
        long start=0;
        long end=0;
        for(int i=0;i<numberOfThread;i++)
        {
            start=i*chunks;
            if(i==3)
            {
                end=totalSize-1;
            }
            else
            {
                end=start+chunks-1;
            }
            DownloadChunk downloadChunk=new DownloadChunk();
            downloadChunk.setStart(start);
            downloadChunk.setEnd(end);
            executor.submit(new DownloaderThread(link,fileName,downloadChunk,totalDownloaded));
        }
        ScheduledExecutorService progressExecutor=Executors.newSingleThreadScheduledExecutor();
        long[] previousBytes={0};
        progressExecutor.scheduleAtFixedRate(() -> {
            long currentBytes=totalDownloaded.get();
            long bytesDownloaded=currentBytes-previousBytes[0];
            double speed=bytesDownloaded/0.5;
            double progress = ((double)currentBytes/totalSize)* 100.0;
            System.out.printf(
                    "Progress: %.2f%% | Speed: %.2f MB/s%n",
                    progress,
                    speed / (1024 * 1024)
            );
            previousBytes[0] = currentBytes;
        }, 0, 500, TimeUnit.MILLISECONDS);
        System.out.println("Downloaded "+totalDownloaded.get());
        con.disconnect();
    }
    public void pauseDownload(String link) {
        if (activeDownloads.containsKey(link) && activeDownloads.get(link).getDownloadStatus() == DownloadStatus.RESUMED)
        {
            System.out.println(activeDownloads.get(link).getFileName());
            activeDownloads.get(link).setDownloadStatus(DownloadStatus.PAUSED);
        }
    }
}
