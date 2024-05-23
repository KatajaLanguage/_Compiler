package com.github.ktj.compiler;

import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

final class MethodCompiler {

    static MethodInfo compileMethod(KtjInterface clazz, ConstPool cp, KtjMethod method, String desc){
        String name = desc.split("%", 2)[0];
        StringBuilder descBuilder = new StringBuilder("(");

        descBuilder.append(")").append(CompilerUtil.toDesc(method.returnType));

        MethodInfo mInfo = new MethodInfo(cp, name, descBuilder.toString());
        mInfo.setAccessFlags(method.getAccessFlag());

        if(!method.isAbstract()){
            Bytecode code = new Bytecode(cp);

            if(name.equals("<init>")){
                code.addAload(0);
                code.addInvokespecial("java/lang/Object", "<init>", "()V");
            }

            if(method.returnType.equals("void")) code.addReturn(null);

            mInfo.setCodeAttribute(code.toCodeAttribute());
        }

        return mInfo;
    }
}
