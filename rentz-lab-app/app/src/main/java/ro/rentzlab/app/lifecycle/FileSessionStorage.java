package ro.rentzlab.app.lifecycle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Atomic file adapter. Staging stays outside the user's canonical files. */
public final class FileSessionStorage implements SessionStorage {
    @FunctionalInterface public interface Replace { void move(Path source, Path target) throws IOException; }
    private final Path directory, staging;
    private final Replace replace;
    public FileSessionStorage(Path directory, Path staging) {
        this(directory, staging, (from, to) -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    public FileSessionStorage(Path directory, Path staging, Replace replace) {
        this.directory=directory.toAbsolutePath().normalize();this.staging=staging.toAbsolutePath().normalize();
        if(this.staging.startsWith(this.directory))throw new IllegalArgumentException("Staging must stay outside canonical session data");
        this.replace=java.util.Objects.requireNonNull(replace);
    }
    @Override public String read() throws IOException {
        Path file=directory.resolve("session.json");
        return Files.exists(file)?new String(Files.readAllBytes(file),StandardCharsets.UTF_8):null;
    }
    @Override public void write(String name,String text) throws IOException {
        if(!name.equals("session.json")&&!name.equals("actor-view.json"))throw new IllegalArgumentException("Session file name");
        Files.createDirectories(directory); Files.createDirectories(staging);
        Path temporary=Files.createTempFile(staging,"."+name+"-",".pending");
        try{
            try(var output=new FileOutputStream(temporary.toFile())){
                output.write(text.getBytes(StandardCharsets.UTF_8));output.getFD().sync();
            }
            replace.move(temporary,directory.resolve(name));
        }catch(IOException|RuntimeException failure){
            try{Files.deleteIfExists(temporary);}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
        Files.deleteIfExists(temporary);
    }
}
