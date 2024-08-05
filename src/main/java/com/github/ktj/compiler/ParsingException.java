package com.github.ktj.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

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
        StackTraceElement element = new StackTraceElement(className, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line);

        if(!Compiler.Instance().debug) return new StackTraceElement[]{element};
        else{
            ArrayList<StackTraceElement> stackTrace = new ArrayList<>();
            stackTrace.add(element);
            Collections.addAll(stackTrace, super.getStackTrace());
            return stackTrace.toArray(new StackTraceElement[0]);
        }
    }

    @Override
    public void printStackTrace() {
        setStackTrace(getStackTrace());
        super.printStackTrace();
    }
}
