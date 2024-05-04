package com.github.x.compiler;

import com.github.x.lang.Compilable;
import com.github.x.lang.XClass;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public final class Compiler {

    public static void main(String[] args){
        var c = new Compiler();
        c.compile("src/test/x/test.x", true);
    }

    private final Parser parser;

    private File outFolder;
    private HashMap<String, Compilable> classes;

    public Compiler(){
        parser = new Parser();
        setOutFolder("out");
    }

    public void setOutFolder(String folder) throws RuntimeException{
        File out = new File(folder);

        if(out.exists()) if(!out.isDirectory()) throw new IllegalArgumentException("Expected folder, got file");
        else if(!out.mkdirs()) throw new RuntimeException("Failed to create out Folder");

        outFolder = out;
    }

    public void compile(String file, boolean execute) throws IllegalArgumentException{
        File f = new File(file);

        if(f.exists()){
            classes = parser.parseFile(f);

            for(String name:classes.keySet()) compileClass(name);

            if(execute){
                //TODO execute
            }
        }else throw new IllegalArgumentException();
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile(outFolder.getPath());
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException(STR."failed to write ClassFile for \{cf.getName()}");
        }
    }

    private void compileClass(String name){
        ClassFile cf = new ClassFile(false, name, null);
        cf.setAccessFlags(AccessFlag.PUBLIC);

        writeFile(cf);
    }
}
