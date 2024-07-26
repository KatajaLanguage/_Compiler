package com.github.ktj.compiler;

public class LexingException extends RuntimeException{
    public LexingException(String message, String file, int line){
        super(message);
        setStackTrace(new StackTraceElement[]{new StackTraceElement("", "", file, line)});
    }
}
