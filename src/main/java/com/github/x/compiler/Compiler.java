package com.github.x.compiler;

import com.github.x.lang.Compilable;
import com.github.x.lang.XTypeClass;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public final class Compiler {

    public static void main(String[] args){
        var c = Compiler.getInstance();
        c.compile("src/test/kataja/Test.ktj", true, true);
    }

    private static Compiler compiler = null;

    private final Parser parser;

    private File outFolder;
    private HashMap<String, Compilable> classes;

    private Compiler(){
        parser = new Parser();
        setOutFolder("out");
    }

    public void setOutFolder(String folder) throws RuntimeException{
        File out = new File(folder);

        if(out.exists()) {
            if (!out.isDirectory()) throw new IllegalArgumentException("Expected folder, got file");
        }else{
            if(!out.mkdirs()) throw new RuntimeException("Failed to create out Folder");
        }

        outFolder = out;
    }

    public void compile(String file, boolean execute, boolean clearOutFolder) throws IllegalArgumentException{
        File f = new File(file);

        if(f.exists()){
            if(f.isDirectory() || !getExtension(f.getName()).equals("ktj")) throw new IllegalArgumentException(STR."Expected kataja File, got \{f.isDirectory() ? "directory" : STR.".\{getExtension(f.getName())} file"}");

            classes = parser.parseFile(f);

            if(clearOutFolder) clearFolder(outFolder);

            for(String name:classes.keySet()) compileClass(name);

            if(execute){
                //TODO execute
            }
        }else throw new IllegalArgumentException();
    }

    public String getExtension(String filename) {
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

    public void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile(outFolder.getPath());
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException(STR."failed to write ClassFile for \{cf.getName()}");
        }
    }

    private void compileClass(String name){
        Compilable clazz = classes.get(name);

        String path = name;
        name = path.split("\\.")[path.split("\\.").length - 1];

        if(clazz instanceof XTypeClass) ClassCompiler.compileTypeClass((XTypeClass) clazz, name, path);
    }

    static String validateClassName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    public static Compiler getInstance(){
        if(compiler == null) compiler = new Compiler();

        return compiler;
    }
}
