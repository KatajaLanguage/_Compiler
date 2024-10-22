/*
 * Copyright (C) 2024 Xaver Weste
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.bytecode.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

public final class Compiler {

    private static Compiler COMPILER = null;

    private final Parser parser;

    private File outFolder;
    public HashMap<String, Compilable> classes;

    boolean debug;

    ArrayList<ClassFile> compiledClasses;

    private Compiler(){
        parser = new Parser();
        debug = false;
        classes = new HashMap<>();
        setOutFolder("out");
    }

    public void setOutFolder(String folder) throws RuntimeException{
        File out = new File(folder);

        if(out.exists()) {
            if (!out.isDirectory()) throw new IllegalArgumentException("Expected folder, got "+getExtension(out.getName())+" file");
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

    public void execute(String path){
        System.out.println();
        String main = null;

        if(path.endsWith(".ktj")) path = path.substring(0, path.length() - 4);
        path = path.replace("/", ".").replace("\\", ".");

        for(String clazzName:classes.keySet()){
            if(clazzName.startsWith(path)&& classes.get(clazzName) instanceof KtjClass && ((KtjClass)(classes.get(clazzName))).methods.containsKey("main%[java.lang.String") && ((KtjClass)(classes.get(clazzName))).methods.get("main%[java.lang.String").modifier.statik && ((KtjClass)(classes.get(clazzName))).methods.get("main%[java.lang.String").modifier.accessFlag == AccessFlag.ACC_PUBLIC){
                if(main != null) throw new RuntimeException("main is defined multiple times");
                main = clazzName;
            }
        }

        if(main == null) throw new RuntimeException("main is not defined");
        else{
            printDebug(">--------------------<");

            try{
                URLClassLoader.newInstance(new URL[]{outFolder.getAbsoluteFile().toURI().toURL()}).loadClass(main).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }catch(InvocationTargetException e){
                e.getTargetException().printStackTrace();
            }catch(ClassNotFoundException |  NoSuchMethodException | SecurityException | IllegalAccessException | MalformedURLException e){
                RuntimeException exception = new RuntimeException("Failed to execute main Method" + (debug ? " : "+e.getClass().getName()+" "+e.getMessage() : ""));
                exception.setStackTrace(e.getStackTrace());
                throw exception;
            }

            printDebug(">--------------------<\nexecution finished successfully");
        }
    }

    public void compile(boolean clearOutFolder, String... files) throws IllegalArgumentException{
        long time = System.nanoTime();

        compiledClasses = new ArrayList<>();

        for(String file:files) {
            File f = new File(file);

            if (!f.exists()) throw new IllegalArgumentException("Unable not find " + f.getAbsolutePath());

            if (f.isDirectory()) {
                for (File folderEntry : f.listFiles()) {
                    if (!folderEntry.isDirectory() && getExtension(folderEntry.getName()).equals("ktj"))
                        classes.putAll(parser.parseFile(folderEntry, file));
                }
            } else if (getExtension(f.getName()).equals("ktj")) {
                classes.putAll(parser.parseFile(f, file));
            } else
                throw new IllegalArgumentException("Expected kataja (.ktj) File, got ." + getExtension(f.getName()) + " file");
        }

        for(String name:classes.keySet()){
            try {
                classes.get(name).validateUses(name);
                classes.get(name).validateTypes();

                if(classes.get(name) instanceof KtjClass){
                    ((KtjClass) classes.get(name)).validateInterfaces();
                    ((KtjClass) classes.get(name)).validateInit(name);
                    ((KtjClass) classes.get(name)).validateClinit(name);
                }
            }catch(RuntimeException e){
                throw new ParsingException(e.getMessage(), name, classes.get(name).line);
            }
        }

        for(String name:classes.keySet()) compileClass(name);

        printDebug("parsing finished successfully");

        if(clearOutFolder){
            clearFolder(outFolder);
            printDebug("out folder cleared successfully");
        }

        validateOutFolder();

        for(ClassFile clazz:compiledClasses) writeFile(clazz);

        if(debug){
            System.out.print("\nCompiling finished successfully in");

            Duration duration = Duration.ofNanos(System.nanoTime() - time);

            time = duration.toMinutes();
            if(time > 0){
                System.out.print(" "+time+" minutes");
                duration = duration.minusMinutes(time);
            }

            time = duration.getSeconds();
            if(time > 0){
                System.out.print(" "+time+" seconds");
                duration = duration.minusSeconds(time);
            }

            time = duration.toMillis();
            if(time > 0){
                System.out.print(" "+time);
                duration = duration.minusMillis(time);
                System.out.print(","+String.valueOf(duration.getNano()).replaceAll("0+$", "")+" milliseconds");
            }else{
                time = duration.toNanos();
                if(time > 0) System.out.print(" "+time+" nanoseconds");
            }

            System.out.println();
        }else printDebug("compiling finished successfully");
    }

    String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void clearFolder(File folder){
        if(folder.exists() && folder.isDirectory() && folder.listFiles() != null){
            for(File file:folder.listFiles()){
                if(file.isDirectory()) clearFolder(file);
                if(!file.delete()) throw new RuntimeException("Failed to delete "+file.getPath());
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
            //ClassPool.getDefault().makeClass(cf).writeFile(outFolder.getPath());
            byte[] classBytecode = ClassPool.getDefault().makeClass(cf).toBytecode();

            ClassReader reader = new ClassReader(classBytecode);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            reader.accept(writer, ClassReader.EXPAND_FRAMES);

            File file = new File(outFolder + "/" + cf.getName().replace(".", "/") + ".class");
            if(file.getParentFile() != null && !file.getParentFile().exists())
                file.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(file)){
                fos.write(writer.toByteArray());
            }catch(IOException e){
                throw new RuntimeException("Failed to write ClassFile for " + cf.getName() + (debug ? " : " + e.getMessage() : ""));
            }
        }catch(IOException | CannotCompileException e){
            throw new RuntimeException("Failed to write ClassFile for " + cf.getName() + (debug ? " : " + e.getMessage() : ""));
        }
    }

    private void compileClass(String name){
        Compilable clazz = classes.get(name);

        String path = name;
        name = path.substring(path.lastIndexOf(".") + 1);
        path = path.length() - name.length() - 1 > 0 ? path.substring(0, path.length() - name.length() - 1) : "";

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
