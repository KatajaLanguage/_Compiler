package com.github.ktj.decompiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.Modifier;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;

import java.io.*;
import java.nio.file.Files;

public final class Decompiler{

    private static File outFolder = new File("decompiled");

    public static void setOutFolder(String folder){
        File out = new File(folder);

        if(out.exists()) {
            if (!out.isDirectory()) throw new IllegalArgumentException("Expected folder, got "+getExtension(out.getName())+" file");
        }else{
            if(!out.mkdirs()) throw new RuntimeException("Failed to create out Folder");
        }

        outFolder = out;
    }

    private static String getExtension(String filename) {
        if(!filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private static void validateOutFolder(){
        if(!outFolder.exists() && !outFolder.mkdirs()) throw new RuntimeException("Failed to create out Folder");
    }

    public static void decompile(String... files){
        for(String file:files) {
            File f = new File(file);

            if(!f.exists()) throw new IllegalArgumentException("Unable not find " + f.getAbsolutePath());

            if(f.isDirectory()){
                decompileFolder(f);
            }else if(getExtension(f.getName()).equals("class")){
                decompile(f);
            }else throw new IllegalArgumentException("Expected .class File, got ." + getExtension(f.getName()) + " file");
        }
    }

    private static void decompileFolder(File folder){
        for(File folderEntry:folder.listFiles()){
            if(folderEntry.isDirectory()) decompileFolder(folderEntry);
            else if(getExtension(folderEntry.getName()).equals("class")) decompile(folderEntry);
        }
    }

    private static void decompile(File file){
        ClassFile cf;
        FileWriter writer;

        try{
            cf = new ClassFile(new DataInputStream(Files.newInputStream(file.toPath())));
            File result = new File(outFolder.getPath()+"\\"+file.getPath().substring(0, file.getPath().lastIndexOf("."))+".ktj");
            if(result.exists()) result.delete();
            result.getParentFile().mkdirs();
            result.createNewFile();
            writer = new FileWriter(result);

            if (cf.isInterface()) decompileInterface(cf, writer);
            else decompileClass(cf, writer);
        }catch(IOException e){
            throw new RuntimeException("failed to decompile "+file.getName());
        }
    }

    private static void decompileInterface(ClassFile cf, FileWriter writer) throws IOException {
        Modifier mod = Modifier.ofInt(cf.getAccessFlags());

        if(mod.accessFlag != AccessFlag.ACC_PACKAGE_PRIVATE){
            switch(mod.accessFlag){
                case ACC_PUBLIC:
                    writer.write("public ");
                    break;
                case ACC_PROTECTED:
                    writer.write("protected ");
                    break;
                case ACC_PRIVATE:
                    writer.write("private ");
                    break;
            }
        }

        writer.write("interface ");
        writer.write(cf.getName().substring(cf.getName().lastIndexOf(".") + 1));
        writer.write(" {\n");

        for(Object mInfo:cf.getMethods()){
            writer.write("\n");
            decompileMethod((MethodInfo) mInfo, null, writer);
        }

        writer.write("\n}");
        writer.close();
    }

    private static void decompileClass(ClassFile cf, FileWriter writer) throws IOException {
        Modifier mod = Modifier.ofInt(cf.getAccessFlags());

        if(mod.accessFlag != AccessFlag.ACC_PACKAGE_PRIVATE){
            switch(mod.accessFlag){
                case ACC_PUBLIC:
                    writer.write("public ");
                    break;
                case ACC_PROTECTED:
                    writer.write("protected ");
                    break;
                case ACC_PRIVATE:
                    writer.write("private ");
                    break;
            }
        }

        writer.write("class ");
        String className = cf.getName().substring(cf.getName().lastIndexOf(".") + 1);
        writer.write(className);

        if(!cf.getSuperclass().equals("java.lang.Object") || cf.getInterfaces().length != 0){
            writer.write(" extends ");
            if(!cf.getSuperclass().equals("java.lang.Object")) {
                writer.write(cf.getSuperclass().substring(cf.getSuperclass().lastIndexOf(".") + 1));
                if(cf.getInterfaces().length != 0) writer.write(", ");
            }
            for(int i = 0;i < cf.getInterfaces().length;i++){
                if(i > 0) writer.write(", ");
                writer.write(cf.getInterfaces()[i].substring(cf.getInterfaces()[i].lastIndexOf(".") + 1));
            }
        }

        writer.write(" {\n");

        for(Object mInfo:cf.getMethods()){
            writer.write("\n");
            decompileMethod((MethodInfo) mInfo, className, writer);
            writer.write("\n");
        }

        writer.write("\n}");
        writer.close();
    }

    private static void decompileMethod(MethodInfo mInfo, String className, FileWriter writer) throws IOException {
        Modifier mod = Modifier.ofInt(mInfo.getAccessFlags());

        writer.write("\t");

        if(mod.accessFlag != AccessFlag.ACC_PACKAGE_PRIVATE){
            switch(mod.accessFlag){
                case ACC_PUBLIC:
                    writer.write("public ");
                    break;
                case ACC_PROTECTED:
                    writer.write("protected ");
                    break;
                case ACC_PRIVATE:
                    writer.write("private ");
                    break;
            }
        }

        if(mod.statik || mInfo.getName().equals("<clinit>")) writer.write("static ");
        if(mod.abstrakt) writer.write("abstract ");
        if(mod.natife) writer.write("native ");
        if(mod.synchronised) writer.write("synchronised ");
        if(mod.strict) writer.write("strict ");

        if(!mInfo.getName().equals("<init>") && !mInfo.getName().equals("<clinit>")){
            writer.write(ofDesc(mInfo.getDescriptor().substring(mInfo.getDescriptor().lastIndexOf(")") + 1)));
            writer.write(" ");
        }

        if(mInfo.getName().equals("<init>") || mInfo.getName().equals("<clinit>")) writer.write(className);
        else writer.write(mInfo.getName());

        if(!mInfo.getName().equals("main")) {
            writer.write("(");
            writer.write(")");
        }

        writer.write("{#method code#}");
    }

    private static String ofDesc(String desc){
        switch(desc){
            case "I":
                return "int";
            case "S":
                return "short";
            case "J":
                return "long";
            case "D":
                return "double";
            case "F":
                return "float";
            case "Z":
                return "boolean";
            case "C":
                return "char";
            case "B":
                return "byte";
            case "V":
                return "void";
            default:
                if(desc.startsWith("[")) return ofDesc(desc.substring(1))+"[]";
                return desc.substring(1).substring(0, desc.length() - 2);
        }
    }
}
