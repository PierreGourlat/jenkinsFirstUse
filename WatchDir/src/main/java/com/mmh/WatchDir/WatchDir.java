package com.mmh.WatchDir;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Example to watch a directory (or tree) for changes to files.
 */

public class WatchDir {
	private static Logger LOGGER = LoggerFactory.getLogger(WatchDir.class);

	private static ExecutorService executor;

	private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
	private final Session session;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /*
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     * @param session 
     */
    WatchDir(Session session, Path dir, boolean recursive) throws IOException {
        this.session = session;
		this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }

    /*
     * Process all events for keys queued to the watcher
     */
    void processEvents() {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                LOGGER.error("Error message - "+ x.toString());
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                Kind<?> kind = event.kind();
                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }
                else if (kind == ENTRY_CREATE){
                    System.out.format("%s: %s\n", event.kind().name(), child);
                    LOGGER.info(name.toString()+" is being uploaded locally.");
                }
                else if (kind == ENTRY_MODIFY){
                    System.out.format("%s: %s\n", event.kind().name(), child);
                    String extension = name.toString().substring(name.toString().lastIndexOf("."));
                    if(extension.equals(".done")){
                        if (!session.isFileHandled(child)){
                            if(Files.exists(child)){
                                try{
                                    Charset charset = Charset.forName("UTF-8");
                                    List<String> lines = Files.readAllLines(child, charset);
                                    String lastline = lines.get(lines.size() - 1);
                                    if(lastline.equals("FIN,FIN,FIN")){
                                        LOGGER.info(name.toString()+" has been received.");
                                        session.startFileHandling(child);
                                        FileManagement filemanager= new FileManagement(session, child, lines);
                                        executor.execute(filemanager);
                                    }
                                }catch (Exception e) {
                                    LOGGER.error("Error message - "+ e.toString());
                                }
                            }
                        }
                    }
                }
                else{
                    System.out.format("%s: %s\n", event.kind().name(), child);
                }
                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        LOGGER.error("Error message - "+ x.toString());
                        // ignore to keep sample readable
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    static void usage() {
        System.err.println("usage: java WatchDir [-r] dir");
        System.exit(-1);
    }
    public static void main(String[] args) throws IOException {
        // parse arguments
        if (args.length == 0 || args.length > 2)
            usage();
        boolean recursive = false;
        int dirArg = 0;
        if (args[0].equals("-r")) {
            if (args.length < 2)
                usage();
            recursive = true;
            dirArg++;
        }
        
        Session session = new Session();
        executor = Executors.newFixedThreadPool(session.getThreadNumber());
        LOGGER.info("Java Program launched");

        // register directory and process its events
        Path dir = Paths.get(args[dirArg]);
        new WatchDir(session, dir, recursive).processEvents();
    }
}

// Usefull doc :
// https://bugs.openjdk.java.net/browse/JDK-8034864
// https://docs.oracle.com/javase/tutorial/essential/io/notification.html
// https://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html
// https://www.baeldung.com/java-nio2-watchservice