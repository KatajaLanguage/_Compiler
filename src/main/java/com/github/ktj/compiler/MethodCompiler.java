package com.github.ktj.compiler;

import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

public class MethodCompiler {

    static MethodInfo compileMethod(KtjInterface clazz, ConstPool cp, KtjMethod method, String desc){
        String name = desc.split("%", 2)[0];
        StringBuilder descBuilder = new StringBuilder("(");

        descBuilder.append(")").append(CompilerUtil.toDesc(method.returnType));

        MethodInfo mInfo = new MethodInfo(cp, name, descBuilder.toString());
        mInfo.setAccessFlags(method.getAccessFlag());

        if(method.isAbstract())
            return mInfo;

        return mInfo;
    }
}
