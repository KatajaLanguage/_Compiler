package com.github.ktj.compiler;

import com.github.ktj.lang.*;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public final class Compiler {

    public static void main(String[] args){
        Compiler c = Compiler.Instance();
        //c.setDebug(true);
        c.compile("src/test/kataja/Test.ktj", true, true);
    }

    private static Compiler COMPILER = null;

    private final Parser parser;

    private File outFolder;
    HashMap<String, Compilable> classes;

    private boolean debug;

    ArrayList<ClassFile> compiledClasses;

    private Compiler(){
        parser = new Parser();
        debug = false;
        setOutFolder("out");
    }

    public void setOutFolder(String folder) throws RuntimeException{
        File out = new File(folder);

        if(out.exists()) {
            if (!out.isDirectory()) throw new IllegalArgumentException(STR."Expected folder, got \{getExtension(out.getName())} file");
        }else{
            if(!out.mkdirs()) throw new RuntimeException("Failed to create out Folder");
        }

        outFolder = out;

        printDebug("out Folder set successfully");
    }

    public void setDebug(boolean debug){
        this.debug = debug;

        printDebug("debug set successfully");
    }

    public void compile(String file, boolean execute, boolean clearOutFolder) throws IllegalArgumentException{
        compiledClasses = new ArrayList<>();
        classes = new HashMap<>();

        File f = new File(file);

        if(f.exists()){
            if(f.isDirectory() || !getExtension(f.getName()).equals("ktj")) throw new IllegalArgumentException(STR."Expected kataja (.ktj) File, got \{f.isDirectory() ? "directory" : STR.".\{getExtension(f.getName())} file"}");

            classes = parser.parseFile(f);

            for(String name:classes.keySet()) compileClass(name);

            for(String name:classes.keySet()){
                try {
                    classes.get(name).validateTypes();
                }catch(RuntimeException e){
                    throw new RuntimeException(STR."\{e} in Class \{name}");
                }
            }

            printDebug("parsing finished successfully");

            if(clearOutFolder){
                clearFolder(outFolder);
                printDebug("out folder cleared successfully");
            }

            validateOutFolder();

            for(ClassFile clazz:compiledClasses) writeFile(clazz);

            printDebug("compiling finished successfully");

            if(execute){
                //TODO execute
            }

            printDebug("process finished successfully");
        }else throw new IllegalArgumentException();
    }

    String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void clearFolder(File folder){
        if(folder.exists() && folder.isDirectory() && folder.listFiles() != null){
            for(File file:folder.listFiles()){
                if(file.isDirectory()) clearFolder(file);
                if(!file.delete()) throw new RuntimeException(STR."Failed to delete \{file.getPath()}");
            }
        }
    }

    private void validateOutFolder(){
        if(!outFolder.exists()){
            if(!outFolder.mkdirs()) throw new RuntimeException("Failed to create out Folder");
            printDebug("out Folder created successfully");
        }else if(debug) printDebug("out Folder validated successfully");
    }

    private void printDebug(String message){
        if(debug) System.out.println(message);
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile(outFolder.getPath());
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException(STR."failed to write ClassFile for \{cf.getName()}");
        }
    }

    private void compileClass(String name){
        Compilable clazz = classes.get(name);

        String path = name;
        name = path.substring(path.lastIndexOf(".") + 1);
        path = path.substring(0, path.length() - name.length() - 1);

        if(clazz instanceof KtjTypeClass) ClassCompiler.compileTypeClass((KtjTypeClass) clazz, name, path);
        else if(clazz instanceof KtjDataClass) ClassCompiler.compileDataClass((KtjDataClass) clazz, name, path);
        else if(clazz instanceof KtjClass) ClassCompiler.compileClass((KtjClass) clazz, name, path);
        else if(clazz instanceof KtjInterface) ClassCompiler.compileInterface((KtjInterface) clazz, name, path);
    }

    public static Compiler Instance(){
        return COMPILER == null ? NewInstance() : COMPILER;
    }

    public static Compiler NewInstance(){
        return (COMPILER = new Compiler());
    }
}
