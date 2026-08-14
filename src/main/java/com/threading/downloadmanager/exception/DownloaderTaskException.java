package com.threading.downloadmanager.exception;

public class DownloaderTaskException extends ResourceNotFoundException
{
    public DownloaderTaskException(long id) {
        super("Downloader task with id " + id + " not found");
    }
}
