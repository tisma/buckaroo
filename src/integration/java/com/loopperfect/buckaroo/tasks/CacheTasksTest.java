package com.loopperfect.buckaroo.tasks;

import com.google.common.hash.HashCode;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.SettableFuture;
import com.loopperfect.buckaroo.*;
import org.junit.Test;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public final class CacheTasksTest {

    public CacheTasksTest() {

    }

    @Test
    public void downloadToCachePopulatesTheCache() throws Exception {

        final FileSystem fs = Jimfs.newFileSystem();

        final RemoteFile remoteFile = RemoteFile.of(
            new URI("https://raw.githubusercontent.com/njlr/test-lib-a/3af013452fe6b448b1cb33bb81bb19da690ec764/BUCK"),
            HashCode.fromString("bb7220f89f404f244ff296b0fba7a70166de35a49094631c9e8d3eacda58d67a"));

        final Path cachePath = CacheTasks.getCachePath(fs, remoteFile);

        CacheTasks.downloadToCache(fs, remoteFile).toList().blockingGet();

        assertTrue(Files.exists(cachePath));
    }

    @Test
    public void downloadToCacheBustsBadCaches() throws Exception {

        final FileSystem fs = Jimfs.newFileSystem();

        final RemoteFile remoteFile = RemoteFile.of(
            new URI("https://raw.githubusercontent.com/njlr/test-lib-a/3af013452fe6b448b1cb33bb81bb19da690ec764/BUCK"),
            HashCode.fromString("bb7220f89f404f244ff296b0fba7a70166de35a49094631c9e8d3eacda58d67a"));

        final Path cachePath = CacheTasks.getCachePath(fs, remoteFile);

        EvenMoreFiles.writeFile(cachePath, "invalid");

        CacheTasks.downloadToCache(fs, remoteFile).toList().blockingGet();

        assertTrue(Files.exists(cachePath));
        assertEquals(remoteFile.sha256, EvenMoreFiles.hashFile(cachePath));
    }

    @Test
    public void cacheWorksTwiceInARow() throws Exception {

        final FileSystem fs = Jimfs.newFileSystem();

        final RemoteFile remoteFile = RemoteFile.of(
            new URI("https://raw.githubusercontent.com/njlr/test-lib-a/3af013452fe6b448b1cb33bb81bb19da690ec764/BUCK"),
            HashCode.fromString("bb7220f89f404f244ff296b0fba7a70166de35a49094631c9e8d3eacda58d67a"));

        final Path path1 = fs.getPath("test.txt");
        final Path path2 = fs.getPath("test.txt");

        CacheTasks.downloadUsingCache(remoteFile, path1)
            .toList()
            .timeout(10000L, TimeUnit.MILLISECONDS)
            .blockingGet();

        final List<Event> events = CacheTasks.downloadUsingCache(remoteFile, path2)
            .toList()
            .timeout(10000L, TimeUnit.MILLISECONDS)
            .blockingGet();

        assertTrue(Files.exists(path1));
        assertTrue(Files.exists(path2));

        assertEquals(remoteFile.sha256, EvenMoreFiles.hashFile(path1));
        assertEquals(remoteFile.sha256, EvenMoreFiles.hashFile(path2));

        assertTrue(events.stream().noneMatch(x -> x instanceof DownloadProgress));
    }

    @Test
    public void cacheWorksForGit() throws Exception {

        final FileSystem fs = FileSystems.getDefault();

        final String tempDirectory = System.getProperty("java.io.tmpdir");

        final GitCommit gitCommit = GitCommit.of(
            "git@github.com:njlr/test-lib-d.git", "c86550e93ca45ed48fd226184c3b996923251e07");

        final Path target1 = Files.createTempDirectory(fs.getPath(tempDirectory), "buckaroo-test")
            .toAbsolutePath();

        CacheTasks.cloneAndCheckoutUsingCache(gitCommit, target1).toList().blockingGet();

        assertTrue(Files.exists(target1.resolve("BUCK")));

        final Path target2 = Files.createTempDirectory(fs.getPath(tempDirectory), "buckaroo-test")
            .toAbsolutePath();

        CacheTasks.cloneAndCheckoutUsingCache(gitCommit, target2).toList().blockingGet();

        assertTrue(Files.exists(target1.resolve("BUCK")));
    }

    @Test
    public void downloadUsingCacheFailsWhenTheHashIsIncorrect() throws Exception {

        final FileSystem fs = Jimfs.newFileSystem();

        final RemoteFile remoteFile = RemoteFile.of(
            new URI("https://raw.githubusercontent.com/njlr/test-lib-a/3af013452fe6b448b1cb33bb81bb19da690ec764/BUCK"),
            HashCode.fromString("aaaaaaaaaaaaaa4f244296b0fba7a70166dea49c9e8d3eacda58d67a"));

        final Path cachePath = CacheTasks.getCachePath(fs, remoteFile);

        final SettableFuture<Throwable> futureException = SettableFuture.create();

        CacheTasks.downloadToCache(fs, remoteFile).subscribe(
            next -> {},
            error -> {
                futureException.set(error);
            },
            () -> {});

        final Throwable exception = futureException.get(5000L, TimeUnit.MILLISECONDS);

        assertTrue(exception instanceof DownloadFileException);
        assertTrue(exception.getCause() instanceof HashMismatchException);
    }
}
