package com.threading.downloadmanager.service;

import com.threading.downloadmanager.DTO.DownloaderTaskDTO;
import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import com.threading.downloadmanager.exception.DownloaderTaskException;
import com.threading.downloadmanager.repository.DownloaderTaskRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

@Service
public class DownloaderService
{
    private final DownloaderTaskRepository downloaderTaskRepository;
    public DownloaderService(DownloaderTaskRepository downloaderTaskRepository)
    {
        this.downloaderTaskRepository = downloaderTaskRepository;
    }
    //Thread Safe HashMap use for storage the task
    ConcurrentHashMap<Long, List<DownloaderThread>> activeDownloads = new ConcurrentHashMap<>();
    Queue<Long> downloaderTaskQueue = new ConcurrentLinkedQueue<>();
    public void addDownloaderTask(DownloaderTaskDTO downloaderTaskDTO) throws IOException {
        String link=downloaderTaskDTO.getUrl();
        URL url=new URL(link);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        String fileName="";
        long totalSize=con.getContentLength();
        String disposition=con.getHeaderField("Content-Disposition");
        if(disposition!=null&&disposition.contains("filename="))
        {
            fileName = disposition.substring(
                    disposition.indexOf("filename=") + 9
            ).replace("\"", "");
        }
        if(fileName.isEmpty())
        {
            fileName="download"+System.currentTimeMillis()+ ".bin";
        }
        File file=new File(fileName);
        if(file.exists())
        {
            fileName=fileName.substring(0,fileName.indexOf('.'))+ System.currentTimeMillis()+fileName.substring(fileName.indexOf('.'));
        }
        downloaderTaskDTO.setFileName(fileName);
        downloaderTaskDTO.setFileSize(totalSize);
        downloaderTaskDTO.setDownloadStatus(DownloadStatus.RESUMED);
        DownloaderTask downloaderTask=downloaderTaskRepository.save(toEntity(downloaderTaskDTO));
        downloaderTaskQueue.add(downloaderTask.getId());
    }
    @Transactional
    public void startDownloading(Long id) throws IOException
    {
        DownloaderTask downloaderTask=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        String link=downloaderTask.getUrl();
        //converting into URL
        URL url=new URL(link);
        //setting up connection
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        String fileName=downloaderTask.getFileName();
        long totalSize=downloaderTask.getFileSize();
        double total=totalSize/(1024.0*1024.0);
        System.out.println("Downloading "+fileName);
        System.out.println("Total Bytes "+totalSize);
        //thread safe variable
        long totalDownloaded=0;
        //number of threads
        int numberOfThread=4;
        ExecutorService executor= Executors.newFixedThreadPool(numberOfThread);
        long chunks=totalSize/numberOfThread;
        long start=0;
        long end=0;
        List<DownloaderThread> downloaderThreadList=new CopyOnWriteArrayList<>();
        List<DownloadChunk> downloadChunkList=new CopyOnWriteArrayList<>();
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
            downloadChunk.setDownloaderTask(downloaderTask);
            downloadChunk.setStart(start);
            downloadChunk.setEnd(end);
            DownloaderThread downloaderThread=new DownloaderThread(link,fileName,downloadChunk,downloaderTask);
            if(downloadChunk.getDownloadStatus()==DownloadStatus.FAILED)
            {
                downloaderThread=new DownloaderThread(link,fileName,downloadChunk,downloaderTask);
            }
            downloaderThreadList.add(downloaderThread);
            downloadChunkList.add(downloadChunk);
            executor.submit(downloaderThread);
        }
        activeDownloads.put(id,downloaderThreadList);
        ScheduledExecutorService progressExecutor=Executors.newSingleThreadScheduledExecutor();
        long[] previousBytes={0};
        final int Breaktime=2000;
        int currtime[]={0};
        progressExecutor.scheduleAtFixedRate(() -> {
            long currentBytes=downloaderTask.getDownloadedSize();
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
            if(currentBytes==totalSize)
            {
                downloaderTask.setDownloadStatus(DownloadStatus.COMPLETED);
            }
            if(downloaderTask.getDownloadStatus()!=DownloadStatus.RESUMED||currtime[0]==Breaktime) progressExecutor.shutdown();
        }, 0, 500, TimeUnit.MILLISECONDS);
        System.out.println("Downloaded "+downloaderTask.getDownloadedSize());

        con.disconnect();
    }
    @Transactional
    public void pauseDownload(Long id)
    {
        DownloaderTask task=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        if (task.getDownloadStatus() == DownloadStatus.RESUMED)
        {
            System.out.println(task.getFileName()+" is paused");
            task.setDownloadStatus(DownloadStatus.PAUSED);
        }
        List<DownloaderThread> downloaderThreadList=activeDownloads.get(id);
        for(DownloaderThread downloaderThread:downloaderThreadList)
        {
            downloaderThread.pause();
        }
        System.out.println("Downloading is paused");
    }
    @Transactional
    public void cancelDownload(Long id)
    {
        DownloaderTask task=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        pauseDownload(task.getId());
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
        downloaderTaskRepository.deleteById(id);
    }
    public void resumeDownloader(long id)
    {
        downloaderTaskQueue.add(id);
    }
    private DownloaderTask toEntity(DownloaderTaskDTO downloaderTaskDTO)
    {
        DownloaderTask downloaderTask=new DownloaderTask();
        downloaderTask.setId(downloaderTaskDTO.getId());
        downloaderTask.setUrl(downloaderTaskDTO.getUrl());
        downloaderTask.setDownloadedSize(downloaderTaskDTO.getDownloadedSize());
        downloaderTask.setFileName(downloaderTaskDTO.getFileName());
        downloaderTask.setFileSize(downloaderTaskDTO.getFileSize());
        downloaderTask.setDownloadStatus(downloaderTaskDTO.getDownloadStatus());
        return downloaderTask;
    }
    private DownloaderTaskDTO toDto(DownloaderTask downloaderTask)
    {
        DownloaderTaskDTO downloaderTaskDTO=new DownloaderTaskDTO(downloaderTask.getUrl());
        downloaderTaskDTO.setId(downloaderTask.getId());
        downloaderTaskDTO.setDownloadedSize(downloaderTask.getDownloadedSize());
        downloaderTaskDTO.setFileName(downloaderTask.getFileName());
        downloaderTaskDTO.setFileSize(downloaderTask.getFileSize());
        downloaderTaskDTO.setDownloadStatus(downloaderTask.getDownloadStatus());
        return downloaderTaskDTO;
    }
}
