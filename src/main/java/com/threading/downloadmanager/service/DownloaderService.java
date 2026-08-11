package com.threading.downloadmanager.service;

import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DownloaderService
{
    //Thread Safe HashMap use for storage the task
    ConcurrentHashMap<String, DownloaderTask> activeDownloads = new ConcurrentHashMap<>();
    public void startDownloading(String link) throws IOException
    {
        //converting into URL
        URL url=new URL(link);
        //setting up connection
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        DownloaderTask task;
        long downloadedBytes =0;
        String fileName="";
        if(activeDownloads.containsKey(link))
        {
            task=activeDownloads.get(link);
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
        task.setFileName(fileName);
        System.out.println("Downloading "+fileName);
        System.out.println("Total Bytes "+totalSize);
        //thread safe variable
        AtomicLong totalDownloaded = new AtomicLong(0);
        //number of threads
        int numberOfThread=4;
        ExecutorService executor= Executors.newFixedThreadPool(numberOfThread);
        long chunks=totalSize/numberOfThread;
        long start=0;
        long end=0;
        List<DownloaderThread> downloaderThreadList=new CopyOnWriteArrayList<>();
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
            DownloaderThread downloaderThread=new DownloaderThread(link,fileName,downloadChunk,totalDownloaded,task);
            if(downloadChunk.getDownloadStatus()==DownloadStatus.FAILED)
            {
                downloaderThread=new DownloaderThread(link,fileName,downloadChunk,totalDownloaded,task);
            }
            downloaderThreadList.add(downloaderThread);
            executor.submit(downloaderThread);
        }
        ScheduledExecutorService progressExecutor=Executors.newSingleThreadScheduledExecutor();
        long[] previousBytes={0};
        final int Breaktime=2000;
        int currtime[]={0};
        progressExecutor.scheduleAtFixedRate(() -> {
            long currentBytes=totalDownloaded.get();
            long bytesDownloaded=currentBytes-previousBytes[0];
            if(bytesDownloaded==0)
            {
                currtime[0]+=500;
            }
            else if(bytesDownloaded>0) currtime[0]=0;
            double speed=bytesDownloaded/0.5;
            double progress = ((double)currentBytes/totalSize)* 100.0;
            System.out.printf(
                    "Progress: %.2f%% | Speed: %.2f MB/s%n",
                    progress,
                    speed / (1024 * 1024)
            );
            previousBytes[0] = currentBytes;
            if(currentBytes==totalSize) task.setDownloadStatus(DownloadStatus.COMPLETED);
            if(task.getDownloadStatus()!=DownloadStatus.RESUMED||currtime[0]==Breaktime) progressExecutor.shutdown();
        }, 0, 500, TimeUnit.MILLISECONDS);
        System.out.println("Downloaded "+totalDownloaded.get());
        task.setDownloaderThreads(downloaderThreadList);
        con.disconnect();
    }
    public void pauseDownload(String link)
    {
        DownloaderTask task=activeDownloads.get(link);
        if(task==null) return;
        if (task.getDownloadStatus() == DownloadStatus.RESUMED)
        {
            System.out.println(task.getFileName()+" is paused");
            task.setDownloadStatus(DownloadStatus.PAUSED);
        }
        List<DownloaderThread> downloaderThreadList=task.getDownloaderThreads();
        for(DownloaderThread downloaderThread:downloaderThreadList)
        {
            downloaderThread.pause();
        }
        System.out.println("Downloading is paused");
    }
    public void cancelDownload(String link)
    {
        DownloaderTask task=activeDownloads.get(link);
        pauseDownload(link);
        task.setDownloadStatus(DownloadStatus.CANCELED);
        String fileName=task.getFileName();
        Path path= Paths.get(fileName);
        try
        {
            Files.deleteIfExists(path);
            System.out.println("Downloading is canceled");
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
    }
}
