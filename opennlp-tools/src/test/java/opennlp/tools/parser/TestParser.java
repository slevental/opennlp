package opennlp.tools.parser;

import opennlp.tools.cmdline.parser.ParserTool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Stas on 1/9/15.
 */
public class TestParser {
    public static void main(String[] args) throws IOException {
//        filter();
        test();
    }

    private static void test() throws IOException {
        File file = new File("sentences.txt");
        BufferedReader br = new BufferedReader(new FileReader(file));

        Parser parser = ParserFactory.create(new ParserModel(TestParser.class.getResourceAsStream("/performance/en-parser-chunking.bin")));
        String line;
        long mill = System.currentTimeMillis();
        int counter = 0;
        while ((line = br.readLine()) != null) {
            if (counter++ % 100 == 0) System.out.println("Processed: " + counter);
            ParserTool.parseLine(line, parser, 1);
        }
        mill = System.currentTimeMillis() - mill;
        System.out.println("Elapsed: " + TimeUnit.SECONDS.convert(mill, TimeUnit.MILLISECONDS) + "s");
    }


}
