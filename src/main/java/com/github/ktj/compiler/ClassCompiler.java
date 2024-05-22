package com.github.ktj.compiler;

import com.github.ktj.lang.*;
import javassist.bytecode.*;

import java.util.Arrays;

final class ClassCompiler {

    static void compileTypeClass(KtjTypeClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, STR."\{path}.\{name}", "java.lang.Enum");
        cf.setAccessFlags(clazz.getAccessFlag());

        //Types
        for(String value: clazz.values){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), value, STR."L\{name};");
            fInfo.setAccessFlags(0x4019);
            cf.addField2(fInfo);
        }

        //$VALUES
        FieldInfo fInfo = new FieldInfo(cf.getConstPool(), "$VALUES", STR."[L\{name};");
        fInfo.setAccessFlags(0x101A);
        cf.addField2(fInfo);

        //values()
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "values", STR."()[L\{name};");
        mInfo.setAccessFlags(0x9);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addGetstatic(name, "$VALUES", STR."[L\{name};");
        code.addInvokevirtual(STR."[L\{name};", "clone","()[Ljava.lang.Object;");
        code.addCheckcast(STR."[L\{name};");
        code.add(Opcode.ARETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //valueOf
        mInfo = new MethodInfo(cf.getConstPool(), "valueOf", STR."(Ljava/lang/String;)L\{name};");
        mInfo.setAccessFlags(0x9);
        code = new Bytecode(cf.getConstPool());
        code.addLdc(STR."L\{name};.class");
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
        mInfo = new MethodInfo(cf.getConstPool(), "$values", STR."()[L\{name};");
        mInfo.setAccessFlags(0x100A);
        code = new Bytecode(cf.getConstPool());
        code.addIconst(clazz.values.length);
        code.addAnewarray(name);
        for(int i = 0;i < clazz.values.length;i++) {
            code.add(0x59); //dup
            code.addIconst(i);
            code.addGetstatic(name, clazz.values[i], STR."L\{name};");
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
            code.addPutstatic(name, clazz.values[i], STR."L\{name};");
        }
        code.addInvokestatic(name, "$values", STR."()[L\{name};");
        code.addPutstatic(name, "$VALUES", STR."[L\{name};");
        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        Compiler.Instance().compiledClasses.add(cf);
    }

    static void compileDataClass(KtjDataClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, STR."\{path}.\{name}", "java/lang/Object");
        cf.setAccessFlags(clazz.getAccessFlag());

        //Fields
        for(String fieldName:clazz.fields.keySet()){
            KtjField field = clazz.fields.get(fieldName);

            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), fieldName, CompilerUtil.toDesc(field.type));
            fInfo.setAccessFlags(field.getAccessFlag());
            cf.addField2(fInfo);
        }

        //<init>
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<init>", STR."(\{CompilerUtil.toDesc(Arrays.stream(clazz.fields.values().toArray(new KtjField[0])).map(field -> field.type).toArray(String[]::new))})V");
        mInfo.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");

        int i = 1;
        for(String field:clazz.fields.keySet()){
            code.addAload(0);
            String desc = CompilerUtil.toDesc(clazz.fields.get(field).type);
            switch(desc){
                case "J" -> code.addLload(i);
                case "D" -> code.addDload(i);
                case "F" -> code.addFload(i);
                case "I", "Z", "B", "C", "S" -> code.addIload(i);
                default -> code.addAload(i);
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
        ClassFile cf = new ClassFile(true, STR."\{path}.\{name}", "java/lang/Object");
        cf.setAccessFlags(clazz.getAccessFlag());

        //Methods
        for(String desc:clazz.methods.keySet()) cf.addMethod2(MethodCompiler.compileMethod(clazz, cf.getConstPool(), clazz.methods.get(desc), desc));

        Compiler.Instance().compiledClasses.add(cf);
    }

    static void compileClass(KtjClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, STR."\{path}.\{name}", "java/lang/Object");
        cf.setAccessFlags(clazz.getAccessFlag());

        //Fields
        for(String fieldName:clazz.fields.keySet()){
            KtjField field = clazz.fields.get(fieldName);

            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), fieldName, CompilerUtil.toDesc(field.type));
            fInfo.setAccessFlags(field.getAccessFlag());
            cf.addField2(fInfo);
        }

        //Methods
        for(String desc:clazz.methods.keySet()){

        }
/*
        //<init>
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<init>", "()V");
        mInfo.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");
        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);
*/
        Compiler.Instance().compiledClasses.add(cf);
    }
}
