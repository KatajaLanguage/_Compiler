package com.github.ktj.compiler;

public class ParsingException extends RuntimeException{

    private String file;
    private int line;

    public ParsingException(String message, String file, int line){
        super(message);
        this.file = file;
        this.line = line;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[]{new StackTraceElement(file, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line)};
    }

    @Override
    public void printStackTrace() {
        setStackTrace(new StackTraceElement[]{new StackTraceElement(file, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line)});
        super.printStackTrace();
    }
}
