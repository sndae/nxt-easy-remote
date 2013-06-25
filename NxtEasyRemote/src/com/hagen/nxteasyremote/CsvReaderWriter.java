package com.hagen.nxteasyremote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintWriter;
import java.io.Writer;

public class CsvReaderWriter {

    private BufferedReader bufferedReader;
    private PrintWriter printWriter;

    private boolean hasNext = true;

    public static final char SEPARATOR = ',';
    public static final String LINE_END = "\n";

    public CsvReaderWriter(Reader reader,Writer writer) {
        bufferedReader = new BufferedReader(reader);
        printWriter = new PrintWriter(writer);
    }

    //Reads the next line from the buffer and converts to a string array
    public String[] readNext() throws IOException {
        String nextLine = getNextLine();
        return hasNext ? parseLine(nextLine) : null;
    }

    //Reads the next line from the file
    private String getNextLine() throws IOException {
        String nextLine = bufferedReader.readLine();
        if (nextLine == null) {
            hasNext = false;
        }
        return hasNext ? nextLine : null;
    }

    //Parses an incoming String and returns the array of singled values
    private String[] parseLine(String nextLine) throws IOException {
        if (nextLine == null)
            return null;

        List<String> values = new ArrayList<String>();
        StringBuffer stringBuffer = new StringBuffer();
        
        for(int i = 0; i < nextLine.length(); i++) {
        	char c = nextLine.charAt(i);
            if(c == SEPARATOR) {
                values.add(stringBuffer.toString());
                stringBuffer = new StringBuffer(); // start work on next token
            }else{
            	stringBuffer.append(c);
            }
        }
        values.add(stringBuffer.toString());
        return (String[]) values.toArray(new String[0]);
    }
        
    //Writes the next line to the file, with each value separated
    public void writeNextLine(String[] nextLine) {
    	if (nextLine == null)
    		return;

        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < nextLine.length; i++) {
            if (i != 0)
            	stringBuffer.append(SEPARATOR);

            stringBuffer.append(nextLine[i]);
        }

        stringBuffer.append(LINE_END);
        printWriter.write(stringBuffer.toString());
    }

    //Closes the underlying reader
    public void closeReader() throws IOException{
    	bufferedReader.close();
    }

    //Flush underlying stream to writer
    public void flushWriter() throws IOException {
    	printWriter.flush();
    }

    //Close the underlying stream writer flushing any buffered content
    public void closeWriter() throws IOException {
    	printWriter.flush();
    	printWriter.close();
    }
}
