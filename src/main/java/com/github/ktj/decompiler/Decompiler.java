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

package com.github.ktj.decompiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.Modifier;
import javassist.bytecode.*;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

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

    private static void clearFolder(File folder){
        if(folder.exists() && folder.isDirectory() && folder.listFiles() != null){
            for(File file:folder.listFiles()){
                if(file.isDirectory()) clearFolder(file);
                if(!file.delete()) throw new RuntimeException("Failed to delete "+file.getPath());
            }
        }
    }

    public static void decompile(String... files){
        validateOutFolder();
        clearFolder(outFolder);
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

            writer.write("# decompiled from "+cf.getName()+"\n\n# uses\n\n");

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

            if(cf.isInterface()) decompileInterface(cf, writer, mod);
            else{
                if(cf.getSuperclass().equals("java.lang.Enum") && cf.getMethods().size() == 4) decompileType(cf, writer, mod);
                else if(cf.getMethods().size() == 1 && ((MethodInfo)(cf.getMethods().get(0))).getName().equals("<init>")) decompileData(cf, writer, mod);
                else{
                    boolean isObject = true;

                    for(Object field:cf.getFields()) if((((FieldInfo)(field)).getAccessFlags() & AccessFlag.STATIC) == 0){
                        isObject = false;
                        break;
                    }

                    if(isObject){
                        for(Object method:cf.getMethods()) if((((MethodInfo)(method)).getAccessFlags() & AccessFlag.STATIC) == 0){
                            if(!((MethodInfo)(method)).getName().equals("<init>") || (((MethodInfo)(method)).getAccessFlags() & AccessFlag.PRIVATE) == 0) {
                                isObject = false;
                                break;
                            }
                        }
                    }

                    if(isObject) decompileObject(cf, writer, mod);
                    else decompileClass(cf, writer, mod);
                }
            }
        }catch(IOException e){
            throw new RuntimeException("failed to decompile "+file.getName());
        }
    }

    private static void decompileInterface(ClassFile cf, FileWriter writer, Modifier mod) throws IOException {
        writer.write("interface ");
        writer.write(cf.getName().substring(cf.getName().lastIndexOf(".") + 1));
        decompileGenerics((SignatureAttribute) cf.getAttribute("Signature"), writer);
        writer.write(" {\n");

        for(Object mInfo:cf.getMethods()) decompileMethod(cf, (MethodInfo) mInfo, null, writer);

        writer.write("\n}");
        writer.close();
    }

    private static void decompileType(ClassFile cf, FileWriter writer, Modifier mod) throws IOException {
        writer.write("type ");

        writer.write(cf.getName().substring(cf.getName().lastIndexOf(".") + 1));
        writer.write(" = ");

        boolean first = true;

        for(Object field:cf.getFields()){
            String name = ((FieldInfo)(field)).getName();
            if(!name.startsWith("$")){
                if(first){
                    first = false;
                }else writer.write(" | ");
                writer.write(name);
            }
        }

        writer.close();
    }

    private static void decompileData(ClassFile cf, FileWriter writer, Modifier mod) throws IOException {
        writer.write("data ");

        writer.write(cf.getName().substring(cf.getName().lastIndexOf(".") + 1));
        writer.write(" = (");

        MethodInfo mInfo = (MethodInfo) cf.getMethods().get(0);
        String[] types = ofMethodDesc(mInfo.getDescriptor());
        MethodParametersAttribute attribute = (MethodParametersAttribute) mInfo.getAttribute("MethodParameters");
        if(attribute != null) {
            for(int i = 0; i < types.length; i++) {
                if (i > 0) writer.write(", ");
                writer.write(((attribute.accessFlags(i) & AccessFlag.FINAL) != 0 ? "const ":"")+types[i] + " " + cf.getConstPool().getUtf8Info(attribute.name(i)));
            }
        }else {
            for (int i = 0; i < types.length; i++) {
                if (i > 0) writer.write(", ");
                writer.write(types[i] + " var" + (mod.statik ? i : i + 1));
            }
        }

        writer.write(")");
        writer.close();
    }

    private static void decompileObject(ClassFile cf, FileWriter writer, Modifier mod) throws IOException {
        if(mod.finaly)writer.write("final ");
        writer.write("object ");
        String className = cf.getName().substring(cf.getName().lastIndexOf(".") + 1);
        writer.write(className);
        writer.write(" {\n");

        for(Object fInfo:cf.getFields()){
            writer.write("\n");
            decompileField(cf, (FieldInfo) fInfo, writer);
            writer.write("\n");
        }

        for(Object mInfo:cf.getMethods()) decompileMethod(cf, (MethodInfo) mInfo, className, writer);

        writer.write("\n}");
        writer.close();
    }

    private static void decompileClass(ClassFile cf, FileWriter writer, Modifier mod) throws IOException {
        if(mod.finaly)writer.write("final ");
        if(mod.abstrakt) writer.write("abstract ");
        writer.write("class ");
        String className = cf.getName().substring(cf.getName().lastIndexOf(".") + 1);
        writer.write(className);

        decompileGenerics((SignatureAttribute) cf.getAttribute("Signature"), writer);

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

        for(Object fInfo:cf.getFields()) decompileField(cf, (FieldInfo) fInfo, writer);

        for(Object mInfo:cf.getMethods()) decompileMethod(cf, (MethodInfo) mInfo, className, writer);

        writer.write("\n}");
        writer.close();
    }

    private static void decompileField(ClassFile cf, FieldInfo fInfo, FileWriter writer) throws IOException {
        Modifier mod = Modifier.ofInt(fInfo.getAccessFlags());

        writer.write("\n\t");

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

        if(mod.statik) writer.write("static ");
        if(mod.synchronised) writer.write("synchronised ");
        if(mod.volatil) writer.write("volatile ");
        if(mod.transint) writer.write("transient ");
        if(mod.constant) writer.write("const ");

        AttributeInfo attribute = fInfo.getAttribute("Signature");
        String desc = attribute == null ? fInfo.getDescriptor() : ((SignatureAttribute)(attribute)).getSignature();
        writer.write(ofDesc(desc));
        writer.write(" ");
        writer.write(fInfo.getName());
        writer.write("\n");
    }

    private static void decompileMethod(ClassFile cf, MethodInfo mInfo, String className, FileWriter writer) throws IOException {
        boolean isEmpty = false;
        if(mInfo.getDescriptor().substring(mInfo.getDescriptor().lastIndexOf(")") + 1).equals("V") && mInfo.getCodeAttribute() != null){
            byte[] code = mInfo.getCodeAttribute().getCode();
            if((code.length == 1 && code[0] == (byte) Opcode.RETURN) || (Arrays.equals(code, new byte[]{42, -73, 0, 28, -79}) && mInfo.getName().equals("<init>"))){
                isEmpty = true;
                if(mInfo.getName().equals("<init>") || mInfo.getName().equals("<clinit>")) return;
            }
        }

        Modifier mod = Modifier.ofInt(mInfo.getAccessFlags());

        writer.write("\n\t");

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
            String signature = mInfo.getAttribute("Signature") == null ? mInfo.getDescriptor() : ((SignatureAttribute)(mInfo.getAttribute("Signature"))).getSignature();
            writer.write(ofDesc(signature.substring(signature.lastIndexOf(")") + 1)));
            writer.write(" ");
        }

        if(mInfo.getName().equals("<init>") || mInfo.getName().equals("<clinit>")) writer.write(className);
        else writer.write(mInfo.getName());

        writer.write("(");

        String desc = mInfo.getAttribute("Signature") == null ? mInfo.getDescriptor() : ((SignatureAttribute)(mInfo.getAttribute("Signature"))).getSignature();
        String[] types = ofMethodDesc(desc);
        MethodParametersAttribute attribute = (MethodParametersAttribute) mInfo.getAttribute("MethodParameters");
        if(attribute != null) {
            for(int i = 0; i < types.length; i++) {
                if (i > 0) writer.write(", ");
                writer.write(((attribute.accessFlags(i) & AccessFlag.FINAL) != 0 ? "const ":"")+types[i] + " " + cf.getConstPool().getUtf8Info(attribute.name(i)));
            }
        }else {
            for (int i = 0; i < types.length; i++) {
                if (i > 0) writer.write(", ");
                writer.write(types[i] + " var" + (mod.statik ? i : i + 1));
            }
        }

        writer.write(")");

        if(isEmpty) writer.write("{}\n");
        else writer.write("{\n\t\t#method code\n\t}\n");
    }

    private static void decompileGenerics(SignatureAttribute attribute, FileWriter writer) throws IOException {
        if(attribute == null || !attribute.getSignature().contains("<")) return;

        writer.write("<");
        String signature = attribute.getSignature();
        signature = signature.substring(1, signature.lastIndexOf(">"));

        String[] types = signature.split(";");
        for(String type:types){
            if(!type.isEmpty()){
                writer.write(type.split(":L")[0]);
                if(!type.split(":L")[1].equals("java/lang/Object")){
                    writer.write(" extends "+type.split(":L")[1].split("/")[type.split(":L")[1].split("/").length - 1]);
                }
            }
        }
        writer.write(">");
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
                if(!desc.contains("<")){
                    desc = desc.substring(1).substring(0, desc.length() - 2);
                    return desc.substring(desc.lastIndexOf("/") + 1);
                }else{
                    StringBuilder sb = new StringBuilder();

                    sb.append(desc.split("<")[0].substring(desc.split("<")[0].lastIndexOf("/") + 1)).append("<");
                    desc = desc.split("<")[1];
                    desc = desc.substring(0, desc.length() - 1);

                    boolean first = true;
                    while(!desc.equals(">")){
                        if(!first) sb.append(", ");
                        else first = false;
                        if(desc.contains("<")) throw new RuntimeException("not supported bytecode");

                        String type = desc.split(";")[0].substring(1);
                        sb.append(type.substring(type.lastIndexOf("/") + 1));
                        desc = desc.split(";", 2)[1];
                    }

                    return sb.append(">").toString();
                }
        }
    }

    private static String[] ofMethodDesc(String desc){
        desc = desc.substring(1, desc.lastIndexOf(")"));
        ArrayList<String> result = new ArrayList<>();

        while(!desc.isEmpty()){
            int i = 0;

            while(desc.startsWith("[")){
                i++;
                desc = desc.substring(1);
            }

            if(desc.startsWith("L") || desc.startsWith("T")){
                StringBuilder sb = new StringBuilder();
                desc = desc.substring(1);
                while(!desc.startsWith(";")){
                    sb.append(desc.toCharArray()[0]);
                    desc = desc.substring(1);
                }
                desc = desc.substring(1);
                sb = new StringBuilder(sb.substring(sb.lastIndexOf("/") + 1));
                while(i > 0){
                    i--;
                    sb.append("[]");
                }
                result.add(sb.toString());
            }else{
                StringBuilder sb = new StringBuilder();
                switch(desc.toCharArray()[0]){
                    case 'I':
                        sb.append("int");
                        break;
                    case 'S':
                        sb.append("short");
                        break;
                    case 'J':
                        sb.append("long");
                        break;
                    case 'D':
                        sb.append("double");
                        break;
                    case 'F':
                        sb.append("float");
                        break;
                    case 'Z':
                        sb.append("boolean");
                        break;
                    case 'C':
                        sb.append("char");
                        break;
                    case 'B':
                        sb.append("byte");
                        break;
                }
                while(i > 0){
                    i--;
                    sb.append("[]");
                }
                result.add(sb.toString());
                desc = desc.substring(1);
            }
        }

        return result.toArray(new String[0]);
    }
}
