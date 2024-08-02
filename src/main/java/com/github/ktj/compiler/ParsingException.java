package com.github.ktj.compiler;

import java.io.File;

public class ParsingException extends RuntimeException{

    private String file;
    private int line;

    public ParsingException(String message, String file, int line){
        super(message);
        this.file = file.replace(".", "\\");
        this.line = line;
    }

    @Override
    public StackTraceElement[] getStackTrace(){
        String className = file;
        if(file.contains("\\") && !new File(file+".ktj").exists()) file = file.substring(0, file.lastIndexOf("\\"));
        return new StackTraceElement[]{new StackTraceElement(className, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line)};
    }

    @Override
    public void printStackTrace() {
        String className = file;
        if(file.contains("\\") && !new File(file+".ktj").exists()) file = file.substring(0, file.lastIndexOf("\\"));
        setStackTrace(new StackTraceElement[]{new StackTraceElement(className, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line)});
        super.printStackTrace();
    }
}
