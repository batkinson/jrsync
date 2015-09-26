package com.github.batkinson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;

public class TestUtils {

    public static RandomAccessFile openFile(String path) throws URISyntaxException, FileNotFoundException {
        URL url = BlockDescTest.class.getResource(path);
        File f = new File(url.toURI());
        return new RandomAccessFile(f, "r");
    }

}
