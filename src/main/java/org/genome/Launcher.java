package org.genome;

import java.io.IOException;

public class Launcher {

    private static final ExcelTransformer transformer = new ExcelTransformer();

    public static void main(String[] args) throws IOException {
        transformer.run();
    }
}
