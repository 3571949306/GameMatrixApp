package com.gamecenter.app.adb.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A connection owns all its channels; closing it cancels blocked channel operations. */
public interface AdbTransport extends Closeable {
    Channel open(String service) throws IOException;
    boolean isOpen();

    interface Channel extends Closeable {
        InputStream input();
        OutputStream output();
    }
}
