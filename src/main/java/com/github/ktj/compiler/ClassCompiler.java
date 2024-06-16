package com.github.ktj.compiler;

import com.github.ktj.lang.*;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.*;

import java.io.IOException;
import java.util.Arrays;

final class ClassCompiler {

    static void compileTypeClass(KtjTypeClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, path.isEmpty() ? name : path+"."+name, "java.lang.Enum");
        cf.setMajorVersion(ClassFile.JAVA_8);
        cf.setAccessFlags(clazz.getAccessFlag());

        //Types
        for(String value: clazz.values){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), value, "L"+name+";");
            fInfo.setAccessFlags(0x4019);
            cf.addField2(fInfo);
        }

        //$VALUES
        FieldInfo fInfo = new FieldInfo(cf.getConstPool(), "$VALUES", "[L"+name+";");
        fInfo.setAccessFlags(0x101A);
        cf.addField2(fInfo);

        //values()
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "values", "()[L"+name+";");
        mInfo.setAccessFlags(0x9);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addGetstatic(name, "$VALUES", "[L"+name+";");
        code.addInvokevirtual("[L"+name+";", "clone","()[Ljava.lang.Object;");
        code.addCheckcast("[L"+name+";");
        code.add(Opcode.ARETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //valueOf
        mInfo = new MethodInfo(cf.getConstPool(), "valueOf", "(Ljava/lang/String;)L"+name+";");
        mInfo.setAccessFlags(0x9);
        code = new Bytecode(cf.getConstPool());
        code.addLdc("L"+name+";.class"); //TODO Check
        code.addAload(0);
        code.addInvokestatic("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
        code.addCheckcast(name);
        code.add(Opcode.ARETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //<inti>
        mInfo = new MethodInfo(cf.getConstPool(), "<init>", "(Ljava/lang/String;I)V");
        mInfo.setAccessFlags(0x2);
        code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addAload(1);
        code.addIload(2);
        code.addInvokespecial("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V");
        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //$values
        mInfo = new MethodInfo(cf.getConstPool(), "$values", "()[L"+name+";");
        mInfo.setAccessFlags(0x100A);
        code = new Bytecode(cf.getConstPool());
        code.addIconst(clazz.values.length);
        code.addAnewarray(name);
        for(int i = 0;i < clazz.values.length;i++) {
            code.add(0x59); //dup
            code.addIconst(i);
            code.addGetstatic(name, clazz.values[i], "L"+name+";");
            code.add(Opcode.AASTORE);
        }
        code.add(Opcode.ARETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //<clinit>
        mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(0x8);
        code = new Bytecode(cf.getConstPool());
        for(int i = 0;i < clazz.values.length;i++) {
            code.addNew(name);
            code.add(Opcode.DUP);
            code.addLdc(clazz.values[i]);
            code.addIconst(i);
            code.addInvokespecial(name, "<init>", "(Ljava/lang/String;I)V");
            code.addPutstatic(name, clazz.values[i], "L"+name+";");
        }
        code.addInvokestatic(name, "$values", "()[L"+name+";");
        code.addPutstatic(name, "$VALUES", "[L"+name+";");
        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        Compiler.Instance().compiledClasses.add(cf);
    }

    static void compileDataClass(KtjDataClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, path.isEmpty() ? name : path+"."+name, "java/lang/Object");
        cf.setMajorVersion(ClassFile.JAVA_8);
        cf.setAccessFlags(clazz.getAccessFlag());

        //Fields
        for(String fieldName:clazz.fields.keySet()){
            KtjField field = clazz.fields.get(fieldName);

            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), fieldName, CompilerUtil.toDesc(field.type));
            fInfo.setAccessFlags(field.getAccessFlag());
            cf.addField2(fInfo);
        }

        //<init>
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<init>", "("+CompilerUtil.toDesc(Arrays.stream(clazz.fields.values().toArray(new KtjField[0])).map(field -> field.type).toArray(String[]::new))+")V");
        mInfo.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");

        int i = 1;
        for(String field:clazz.fields.keySet()){
            code.addAload(0);
            String desc = CompilerUtil.toDesc(clazz.fields.get(field).type);
            switch(desc){
                case "J":
                    code.addLload(i);
                    break;
                case "D":
                    code.addDload(i);
                    break;
                case "F":
                    code.addFload(i);
                    break;
                case "I":
                case "Z":
                case "B":
                case "C":
                case "S":
                    code.addIload(i);
                    break;
                default:
                    code.addAload(i);
                    break;
            }
            code.addPutfield(name, field, desc);
            i++;
        }
        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        Compiler.Instance().compiledClasses.add(cf);
    }

    static void compileInterface(KtjInterface clazz, String name, String path){
        ClassFile cf = new ClassFile(true, path.isEmpty() ? name : path+"."+name, "java/lang/Object");
        cf.setMajorVersion(ClassFile.JAVA_8);
        cf.setAccessFlags(clazz.getAccessFlag());

        //Methods
        for(String desc:clazz.methods.keySet()) cf.addMethod2(MethodCompiler.compileMethod(clazz, path.isEmpty() ? name : path+"."+name, cf.getConstPool(), clazz.methods.get(desc), desc));

        Compiler.Instance().compiledClasses.add(cf);
    }

    static void compileClass(KtjClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, path.isEmpty() ? name : path+"."+name, "java.lang.Object");
        cf.setMajorVersion(ClassFile.JAVA_8);
        cf.setAccessFlags(clazz.getAccessFlag());

        //Fields
        for(String fieldName:clazz.fields.keySet()){
            KtjField field = clazz.fields.get(fieldName);

            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), fieldName, CompilerUtil.toDesc(field.type));
            fInfo.setAccessFlags(field.getAccessFlag());
            cf.addField2(fInfo);
        }

        //cf.addAttribute(new AttributeInfo());

        //Methods
        clazz.validateInit();
        for(String desc:clazz.methods.keySet()) cf.addMethod2(MethodCompiler.compileMethod(clazz, path.isEmpty() ? name : path+"."+name, cf.getConstPool(), clazz.methods.get(desc), desc));

        //<clinit>
        String clinit = clazz.createClinit();
        if(clinit != null) cf.addMethod2(MethodCompiler.compileClinit(clazz, path.isEmpty() ? name : path+"."+name, cf.getConstPool(), clinit));

        Compiler.Instance().compiledClasses.add(cf);
    }
}
