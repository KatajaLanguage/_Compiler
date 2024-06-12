package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.KtjClass;
import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.*;

import java.util.ArrayList;
import java.util.Set;

final class MethodCompiler {

    private static MethodCompiler INSTANCE = null;
    private final SyntacticParser parser;
    private KtjInterface clazz;
    private OperandStack os;
    private Bytecode code;
    private ConstPool cp;
    private String clazzName;

    private MethodCompiler(){
        parser = new SyntacticParser();
    }

    private void compileCode(Bytecode code, String ktjCode, KtjInterface clazz, String clazzName, KtjMethod method, ConstPool cp){
        this.code = code;
        this.cp = cp;
        this.clazz = clazz;
        this.clazzName = clazzName;
        os = OperandStack.forMethod(method);
        AST[] ast = parser.parseAst(clazz, clazzName, method, ktjCode);

        for (AST value : ast) compileAST(value);
    }

    private void compileAST(AST ast){
        if(ast instanceof AST.While) compileWhile((AST.While) ast);
        else if(ast instanceof AST.If) compileIf((AST.If) ast);
        else if(ast instanceof AST.Return) compileReturn((AST.Return) ast);
        else if(ast instanceof AST.Load) compileCall((AST.Load) ast, true);
        else if(ast instanceof AST.VarAssignment) compileVarAssignment((AST.VarAssignment) ast);
    }

    private void compileReturn(AST.Return ast){
        if(ast.calc != null) compileCalc(ast.calc);

        switch(ast.type){
            case "void" -> code.add(Opcode.RETURN);
            case "int", "boolean", "char", "byte", "short" -> code.add(Opcode.IRETURN);
            case "float" -> code.add(Opcode.FRETURN);
            case "double" -> code.add(Opcode.DRETURN);
            case "long" -> code.add(Opcode.LRETURN);
            default -> code.add(Opcode.ARETURN);
        }
    }

    private void compileWhile(AST.While ast){
        os.newScope();
        int start = code.getSize();
        compileCalc(ast.condition);
        code.addOpcode(Opcode.IFEQ);
        int branch = code.getSize();
        code.addIndex(0);

        for(AST statement: ast.ast)
            compileAST(statement);

        code.addOpcode(Opcode.GOTO);
        code.addIndex(-(code.getSize() - start) + 1);
        code.write16bit(branch, code.getSize() - branch + 1);
        os.clearScope(code);
    }

    private void compileIf(AST.If ast){
        ArrayList<Integer> gotos = new ArrayList<>();
        int branch = 0;

        while (ast != null){
            os.newScope();
            if(ast.condition != null) {
                compileCalc(ast.condition);
                code.addOpcode(Opcode.IFEQ);
                branch = code.getSize();
                code.addIndex(0);
            }

            for(AST statement:ast.ast)
                compileAST(statement);

            if(ast.elif != null){
                code.addOpcode(Opcode.GOTO);
                gotos.add(code.getSize());
                code.addIndex(0);
            }

            if(ast.condition != null)
                code.write16bit(branch, code.getSize() - branch + 1);

            ast = ast.elif;
            os.clearScope(code);
        }

        for(int i:gotos)
            code.write16bit(i, code.getSize() - i + 1);
    }

    private void compileCall(AST.Load ast, boolean all){
        if(ast.name != null && ast.call == null && !all) return;

        boolean first = true;

        if(ast.name != null){
            switch (ast.clazz){
                case "int", "boolean", "char", "byte", "short" -> {
                    code.addIload(os.get(ast.name));
                    os.push(1);
                }
                case "float" -> {
                    code.addFload(os.get(ast.name));
                    os.push(1);
                }
                case "double" -> {
                    code.addDload(os.get(ast.name));
                    os.push(2);
                }
                case "long" -> {
                    code.addLload(os.get(ast.name));
                    os.push(2);
                }
                default -> {
                    code.addAload(os.get(ast.name));
                    os.push(1);
                }
            }
            first = false;
        }

        if(!all && ast.name == null && ast.call.prev == null) return;

        if(ast.call != null) compileCall(all ? ast.call : ast.call.prev, first);
    }

    private void compileCall(AST.Call call, boolean first){
        if(call == null) return;

        if(call.prev != null) compileCall(call.prev, false);

        if(call.argTypes == null) {
            if(call.clazz.startsWith("[")) code.add(Opcode.ARRAYLENGTH);
            else if (call.statik) code.addGetstatic(call.clazz, call.call, CompilerUtil.toDesc(call.type));
            else {
                if (first && call.clazz.equals(clazzName)) code.addAload(0);
                code.addGetfield(call.clazz, call.call, CompilerUtil.toDesc(call.type));
            }
        }else{
            if(!call.statik && first && call.clazz.equals(clazzName) && !call.call.equals("<init>")) code.addAload(0);
            else if(call.call == null || !call.call.equals("<init>")) os.pop();

            for(AST.Calc calc:call.argTypes) compileCalc(calc);

            if(call.clazz.startsWith("[")){
                if(call.call.equals("<init>")) code.addAnewarray(call.clazz.substring(1));
                else code.add(Opcode.AALOAD);
            }else if(call.call.equals("<init>")){
                code.addNew(call.clazz);
                code.add(Opcode.DUP);
                code.addInvokespecial(call.clazz, "<init>", CompilerUtil.toDesc("void", call.argTypes));
            }else if(call.statik) code.addInvokestatic(call.clazz, call.call, CompilerUtil.toDesc(call.type, call.argTypes));
            else code.addInvokevirtual(call.clazz, call.call, CompilerUtil.toDesc(call.type, call.argTypes));
        }

        os.push(Set.of("double", "long").contains(call.type) ? 2 : 1);
    }

    private void compileVarAssignment(AST.VarAssignment ast){
        if(ast.load.name == null && ast.load.call != null && !ast.load.call.statik && ast.load.call.clazz.equals(clazzName)) code.addAload(0);
        compileCall(ast.load, false);

        if(ast.load.call != null && ast.load.call.call == null) compileCalc(ast.load.call.argTypes[0]);

        compileCalc(ast.calc);

        if(ast.load.call == null || ast.load.call.call == null){
            int where = os.get(ast.load.name);

            if(where == -1) {
                os.pop();
                where = os.push(ast.load.name, Set.of("double", "long").contains(ast.load.type) ? 2 : 1);
            }

            if(ast.load.call != null && ast.load.call.call == null){
                code.add(Opcode.AASTORE);
            }else{
                switch (ast.load.type) {
                    case "int", "boolean", "char", "byte", "short" -> code.addIstore(where);
                    case "float" -> code.addFstore(where);
                    case "double" -> code.addDstore(where);
                    case "long" -> code.addLstore(where);
                    default -> code.addAstore(where);
                }
            }
        }else{
            if(ast.load.call.statik) code.addPutstatic(ast.load.call.clazz, ast.load.call.call, CompilerUtil.toDesc(ast.load.call.type));
            else code.addPutfield(ast.load.call.clazz, ast.load.call.call, CompilerUtil.toDesc(ast.load.call.type));
        }
    }

    private void compileCalc(AST.Calc ast){
        if(ast.right != null) compileCalc(ast.right);
        compileValue(ast.value);
        if(ast.op != null) compileOperator(ast);
    }

    private void compileOperator(AST.Calc ast){
        switch(ast.type){
            case "int", "char", "byte", "short", "boolean" -> {
                switch (ast.op){
                    case "+" -> code.add(Opcode.IADD);
                    case "-" -> code.add(Opcode.ISUB);
                    case "*" -> code.add(Opcode.IMUL);
                    case "/" -> code.add(Opcode.IDIV);
                    case "==", "!=", "<", "<=", ">", ">=" -> {
                        switch(ast.op) {
                            case "==" -> code.addOpcode(Opcode.IF_ICMPEQ);
                            case "!=" -> code.addOpcode(Opcode.IF_ICMPNE);
                            case "<" -> code.addOpcode(Opcode.IF_ICMPLT);
                            case "<=" -> code.addOpcode(Opcode.IF_ICMPLE);
                            case ">" -> code.addOpcode(Opcode.IF_ICMPGT);
                            case ">=" -> code.addOpcode(Opcode.IF_ICMPGE);
                        }
                        int branchLocation = code.getSize();
                        code.addIndex(0);
                        code.addIconst(0);
                        code.addOpcode(Opcode.GOTO);
                        int endLocation = code.getSize();
                        code.addIndex(0);
                        code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
                        code.addIconst(1);
                        code.write16bit(endLocation, code.getSize() - endLocation + 1);
                    }
                }
            }
            case "double" -> {
                switch (ast.op){
                    case "+" -> code.add(Opcode.DADD);
                    case "-" -> code.add(Opcode.DSUB);
                    case "*" -> code.add(Opcode.DMUL);
                    case "/" -> code.add(Opcode.DDIV);
                    case "==", "!=", "<=", "<", ">=", ">" -> {
                        code.add(Opcode.DCMPG);
                        compileBoolOp(ast);
                    }
                }
            }
            case "float" -> {
                switch (ast.op){
                    case "+" -> code.add(Opcode.FADD);
                    case "-" -> code.add(Opcode.FSUB);
                    case "*" -> code.add(Opcode.FMUL);
                    case "/" -> code.add(Opcode.FDIV);
                    case "==", "!=", "<=", "<", ">=", ">" -> {
                        code.add(Opcode.FCMPG);
                        compileBoolOp(ast);
                    }
                }
            }
            case "long" -> {
                switch (ast.op){
                    case "+" -> code.add(Opcode.LADD);
                    case "-" -> code.add(Opcode.LSUB);
                    case "*" -> code.add(Opcode.LMUL);
                    case "/" -> code.add(Opcode.LDIV);
                    case "==", "!=", "<=", "<", ">=", ">" -> {
                        code.add(Opcode.LCMP);
                        compileBoolOp(ast);
                    }
                }
            }
        }
        os.pop();
        if(CompilerUtil.BOOL_OPERATORS.contains(ast.op)) {
            os.pop();
            os.push(1);
        }
    }

    private void compileBoolOp(AST.Calc ast){
        switch(ast.op){
            case "==", "!=" -> {
                code.addIconst(0);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<", ">" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(ast.op.equals("<") ? "-1" : "1", Token.Type.INTEGER);
                compileValue(value);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<=", ">=" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(ast.op.equals("<=") ? "1" : "-1", Token.Type.INTEGER);
                compileValue(value);
                code.addOpcode(Opcode.IF_ICMPNE);
            }
        }
        int branchLocation = code.getSize();
        code.addIndex(0);
        if(ast.op.equals("!=")) code.addIconst(1);
        else code.addIconst(0);
        code.addOpcode(Opcode.GOTO);
        int endLocation = code.getSize();
        code.addIndex(0);
        code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
        if(ast.op.equals("!=")) code.addIconst(0);
        else code.addIconst(1);
        code.write16bit(endLocation, code.getSize() - endLocation + 1);
    }

    private void compileValue(AST.Value ast){
        if(ast.load != null){
            compileCall(ast.load, true);
            os.push(ast.load.type.equals("double") || ast.load.type.equals("long") ? 2 : 1);
            return;
        }

        switch (ast.token.t().toString()){
            case "int", "short", "byte", "char" -> {
                int intValue;
                if(ast.type.equals("char"))
                    intValue = ast.token.s().toCharArray()[1];
                else
                    intValue = Integer.parseInt(ast.token.s());

                if(intValue < 6 && intValue >= 0)
                    code.addIconst(intValue);
                else
                    code.add(Opcode.BIPUSH ,intValue);

                os.push(1);
            }
            case "float" -> {
                int index = 0;
                float floatValue = Float.parseFloat(ast.token.s());
                cp.addFloatInfo(floatValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getFloatInfo(index) == floatValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(1);
            }
            case "double" -> {
                int index = 0;
                double doubleValue = Double.parseDouble(ast.token.s());
                cp.addDoubleInfo(doubleValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getDoubleInfo(index) == doubleValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
            }
            case "long" -> {
                int index = 0;
                long longValue = Long.parseLong(ast.token.s());
                cp.addLongInfo(longValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getLongInfo(index) == longValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
            }
            default -> {
                if(ast.type.equals("boolean")) code.addIconst(ast.token.s().equals("true") ? 1 : 0);
                if(ast.token.s().equals("null")) code.add(Opcode.ACONST_NULL);
                os.push(1);
            }
        }
    }

    static MethodCompiler getInstance(){
        if(INSTANCE == null) INSTANCE = new MethodCompiler();

        return INSTANCE;
    }

    static MethodInfo compileMethod(KtjInterface clazz, String clazzName, ConstPool cp, KtjMethod method, String desc){
        String name = desc.split("%", 2)[0];
        StringBuilder descBuilder = new StringBuilder("(");

        for(KtjMethod.Parameter p:method.parameter) descBuilder.append(CompilerUtil.toDesc(p.type()));

        descBuilder.append(")").append(CompilerUtil.toDesc(method.returnType));

        MethodInfo mInfo = new MethodInfo(cp, name, descBuilder.toString());
        mInfo.setAccessFlags(method.getAccessFlag());

        if(!method.isAbstract()){
            Bytecode code = new Bytecode(cp);

            if(name.equals("<init>")){
                code.addAload(0);
                code.addInvokespecial("java/lang/Object", "<init>", "()V");
            }

            if(name.equals("<init>")) {
                assert clazz instanceof KtjClass;

                String initValues = ((KtjClass) clazz).initValues();
                getInstance().compileCode(code, STR."\{initValues != null ? initValues : ""} \n \{method.code}", clazz, clazzName, method, cp);
            }else getInstance().compileCode(code, method.code, clazz, clazzName, method, cp);

            mInfo.setCodeAttribute(code.toCodeAttribute());
        }

        return mInfo;
    }

    static MethodInfo compileClinit(KtjInterface clazz, String clazzName, ConstPool cp, String code){
        MethodInfo mInfo = new MethodInfo(cp, "<clinit>", "()V");
        mInfo.setAccessFlags(AccessFlag.STATIC);

        Bytecode bytecode = new Bytecode(cp);

        getInstance().compileCode(bytecode, code, clazz, clazzName, new KtjMethod(null, "void", code, new KtjMethod.Parameter[0], clazz.uses, clazz.file, Integer.MIN_VALUE), cp);

        mInfo.setCodeAttribute(bytecode.toCodeAttribute());

        return mInfo;
    }
}
