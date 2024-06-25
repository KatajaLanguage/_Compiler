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
            case "void":
                code.add(Opcode.RETURN);
                break;
            case "int":
            case "boolean":
            case "char":
            case "byte":
            case "short":
                code.add(Opcode.IRETURN);
                break;
            case "float":
                code.add(Opcode.FRETURN);
                break;
            case "double":
                code.add(Opcode.DRETURN);
                break;
            case "long":
                code.add(Opcode.LRETURN);
                break;
            default:
                code.add(Opcode.ARETURN);
                break;
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
                case "int":
                case "boolean":
                case "char":
                case "byte":
                case "short":
                    code.addIload(os.get(ast.name));
                    os.push(1);
                    break;
                case "float":
                    code.addFload(os.get(ast.name));
                    os.push(1);
                    break;
                case "double":
                    code.addDload(os.get(ast.name));
                    os.push(2);
                    break;
                case "long":
                    code.addLload(os.get(ast.name));
                    os.push(2);
                    break;
                default:
                    code.addAload(os.get(ast.name));
                    os.push(1);
                    break;
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

            if(!call.call.equals("<init>") || call.type.startsWith("[")) for(AST.Calc calc:call.argTypes) compileCalc(calc);

            if(call.clazz.startsWith("[")){
                if(call.call.equals("<init>")){
                    if(call.clazz.startsWith("[[")) code.addMultiNewarray(call.clazz, call.clazz.lastIndexOf("[") + 1);
                    else code.addAnewarray(call.clazz.substring(1));
                }else{
                    switch(call.clazz){
                        case "[int":
                        case "[boolean":
                        case "[char":
                        case "[byte":
                        case "[short":
                            code.add(Opcode.IALOAD);
                            break;
                        case "[float":
                            code.add(Opcode.FALOAD);
                            break;
                        case "[double":
                            code.add(Opcode.DALOAD);
                            break;
                        case "[long":
                            code.add(Opcode.LALOAD);
                            break;
                        default:
                            code.add(Opcode.AALOAD);
                            break;
                    }
                }
            }else if(call.call.equals("<init>")){
                code.addNew(call.clazz);
                code.add(Opcode.DUP);
                for(AST.Calc calc:call.argTypes) compileCalc(calc);
                code.addInvokespecial(call.clazz, "<init>", CompilerUtil.toDesc("void", call.argTypes));
            }else if(call.statik) code.addInvokestatic(call.clazz, call.call, CompilerUtil.toDesc(call.type, call.argTypes));
            else code.addInvokevirtual(call.clazz, call.call, CompilerUtil.toDesc(call.type, call.argTypes));
        }

        os.push(call.type.equals("double") || call.type.equals("long") ? 2 : 1);
    }

    private void compileVarAssignment(AST.VarAssignment ast){
        if(ast.load.name == null && ast.load.call != null && !ast.load.call.statik && ast.load.call.clazz.equals(clazzName)) code.addAload(0);
        compileCall(ast.load, false);

        if(ast.load.call == null){
            compileCalc(ast.calc);

            int where = os.isEmpty() ? 0 : os.get(ast.load.name);

            if(where == -1) {
                os.pop();
                where = os.push(ast.load.name, ast.load.type.equals("double") || ast.load.type.equals("long") ? 2 : 1);
            }

            switch(ast.type){
                case "int":
                case "boolean":
                case "char":
                case "byte":
                case "short":
                    code.addIstore(where);
                    break;
                case "float":
                    code.addFstore(where);
                    break;
                case "double":
                    code.addDstore(where);
                    break;
                case "long":
                    code.addLstore(where);
                    break;
                default:
                    code.addAstore(where);
                    break;
            }
        }else{
            if(ast.load.clazz.startsWith("[")){
                compileCalc(ast.load.call.argTypes[0]);
                compileCalc(ast.calc);

                switch(ast.load.clazz){
                    case "[int":
                    case "[boolean":
                    case "[char":
                    case "[byte":
                    case "[short":
                        code.add(Opcode.IASTORE);
                        break;
                    case "[float":
                        code.add(Opcode.FASTORE);
                        break;
                    case "[double":
                        code.add(Opcode.DASTORE);
                        break;
                    case "[long":
                        code.add(Opcode.LASTORE);
                        break;
                    default:
                        code.add(Opcode.AASTORE);
                        break;
                }
            }else if(ast.load.call.statik) code.addPutstatic(ast.load.call.clazz, ast.load.call.call, CompilerUtil.toDesc(ast.load.call.type));
            else code.addPutfield(ast.load.call.clazz, ast.load.call.call, CompilerUtil.toDesc(ast.load.call.type));
        }
    }

    private void compileCalc(AST.Calc ast){
        if(ast.right != null) compileCalc(ast.right);
        if(ast.arg instanceof AST.Cast) compileCast((AST.Cast) ast.arg);
        else if(ast.arg instanceof AST.ArrayCreation) compileArrayCreation((AST.ArrayCreation) ast.arg);
        else compileValue((AST.Value) ast.arg);
        if(ast.op != null) compileOperator(ast);
    }

    private void compileCast(AST.Cast ast){
        compileCalc(ast.calc);

        String temp;

        switch(ast.calc.type){
            case "int":
            case "short":
            case "byte":
                switch(ast.type){
                    case "double":
                        code.add(Opcode.I2D);
                        temp = os.pop();
                        os.push(temp, 2);
                        break;
                    case "long":
                        code.add(Opcode.I2L);
                        temp = os.pop();
                        os.push(temp, 2);
                        break;
                    case "float":
                        code.add(Opcode.I2F);
                        break;
                    case "byte":
                        code.add(Opcode.I2B);
                        break;
                    case "char":
                        code.add(Opcode.I2C);
                        break;
                    case "short":
                        code.add(Opcode.I2S);
                        break;
                }
                break;
            case "double":
                switch(ast.type){
                    case "int":
                        code.add(Opcode.D2I);
                        temp = os.pop();
                        os.push(temp, 1);
                        break;
                    case "long":
                        code.add(Opcode.D2L);
                        break;
                    case "float":
                        code.add(Opcode.D2F);
                        temp = os.pop();
                        os.push(temp, 1);
                        break;
                }
                break;
            case "long":
                switch(ast.type){
                    case "double":
                        code.add(Opcode.L2D);
                        break;
                    case "int":
                        code.add(Opcode.L2I);
                        temp = os.pop();
                        os.push(temp, 1);
                        break;
                    case "float":
                        code.add(Opcode.L2F);
                        temp = os.pop();
                        os.push(temp, 1);
                        break;
                }
                break;
            case "float":
                switch(ast.type){
                    case "double":
                        code.add(Opcode.F2D);
                        temp = os.pop();
                        os.push(temp, 2);
                        break;
                    case "long":
                        code.add(Opcode.F2L);
                        temp = os.pop();
                        os.push(temp, 2);
                        break;
                    case "int":
                        code.add(Opcode.F2I);
                        break;
                }
                break;
            default:
                code.addCheckcast(ast.cast);
        }
    }

    private void compileOperator(AST.Calc ast){
        switch(ast.type){
            case "int":
            case "char":
            case "byte":
            case "short":
            case "boolean":
                switch (ast.op){
                    case "+":
                        code.add(Opcode.DADD);
                        break;
                    case "-":
                        code.add(Opcode.DSUB);
                        break;
                    case "*":
                        code.add(Opcode.DMUL);
                        break;
                    case "/" :
                        code.add(Opcode.DDIV);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        switch(ast.op) {
                            case "==":
                                code.addOpcode(Opcode.IF_ICMPEQ);
                                break;
                            case "!=":
                                code.addOpcode(Opcode.IF_ICMPNE);
                                break;
                            case "<":
                                code.addOpcode(Opcode.IF_ICMPLT);
                                break;
                            case "<=":
                                code.addOpcode(Opcode.IF_ICMPLE);
                                break;
                            case ">":
                                code.addOpcode(Opcode.IF_ICMPGT);
                                break;
                            case ">=":
                                code.addOpcode(Opcode.IF_ICMPGE);
                                break;
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
                        break;
                }
                break;
            case "double":
                switch (ast.op){
                    case "+":
                        code.add(Opcode.DADD);
                        break;
                    case "-":
                        code.add(Opcode.DSUB);
                        break;
                    case "*":
                        code.add(Opcode.DMUL);
                        break;
                    case "/" :
                        code.add(Opcode.DDIV);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.DCMPG);
                        break;
                }
                compileBoolOp(ast);
                break;
            case "float":
                switch (ast.op){
                    case "+":
                        code.add(Opcode.FADD);
                        break;
                    case "-":
                        code.add(Opcode.FSUB);
                        break;
                    case "*":
                        code.add(Opcode.FMUL);
                        break;
                    case "/" :
                        code.add(Opcode.FDIV);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.FCMPG);
                        break;
                }
                compileBoolOp(ast);
                break;
            case "long":
                switch (ast.op){
                    case "+":
                        code.add(Opcode.LADD);
                        break;
                    case "-":
                        code.add(Opcode.LSUB);
                        break;
                    case "*":
                        code.add(Opcode.LMUL);
                        break;
                    case "/" :
                        code.add(Opcode.LDIV);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.LCMP);
                        break;
                }
                compileBoolOp(ast);
                break;
            default:
                if(ast.op.equals("==")) code.add(Opcode.IF_ACMPEQ);
                else code.add(Opcode.IF_ACMPNE);
                int branchLocation = code.getSize();
                code.addIndex(0);
                code.addIconst(0);
                code.addOpcode(Opcode.GOTO);
                int endLocation = code.getSize();
                code.addIndex(0);
                code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
                code.addIconst(1);
                code.write16bit(endLocation, code.getSize() - endLocation + 1);
                break;
        }
        os.pop();
        if(CompilerUtil.BOOL_OPERATORS.contains(ast.op) || CompilerUtil.NUM_BOOL_OPERATORS.contains(ast.op)) {
            os.pop();
            os.push(1);
        }
    }

    private void compileBoolOp(AST.Calc ast){
        AST.Value value;
        switch(ast.op){
            case "==":
            case "!=":
                code.addIconst(0);
                code.addOpcode(Opcode.IF_ICMPEQ);
                break;
            case "<":
            case ">":
                value = new AST.Value();
                value.type = "int";
                value.token = new Token(ast.op.equals("<") ? "-1" : "1", Token.Type.INTEGER);
                compileValue(value);
                code.addOpcode(Opcode.IF_ICMPEQ);
                break;
            case "<=":
            case ">=":
                value = new AST.Value();
                value.type = "int";
                value.token = new Token(ast.op.equals("<=") ? "1" : "-1", Token.Type.INTEGER);
                compileValue(value);
                code.addOpcode(Opcode.IF_ICMPNE);
                break;
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

        int index;

        switch (ast.token.t.toString()){
            case "int":
            case "short":
            case "byte":
            case "char":
                int intValue;
                if(ast.type.equals("char"))
                    intValue = ast.token.s.toCharArray()[1];
                else
                    intValue = Integer.parseInt(ast.token.s);

                if(intValue < 6 && intValue >= 0)
                    code.addIconst(intValue);
                else
                    code.add(Opcode.BIPUSH ,intValue);

                os.push(1);
                break;
            case "float":
                index = 0;
                float floatValue = Float.parseFloat(ast.token.s);
                cp.addFloatInfo(floatValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getFloatInfo(index) == floatValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(1);
                break;
            case "double":
                index = 0;
                double doubleValue = Double.parseDouble(ast.token.s);
                cp.addDoubleInfo(doubleValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getDoubleInfo(index) == doubleValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
                break;
            case "long":
                index = 0;
                long longValue = Long.parseLong(ast.token.s);
                cp.addLongInfo(longValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getLongInfo(index) == longValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
                break;
            case "java.lang.String":
                code.addLdc(ast.token.s.substring(1, ast.token.s.length() - 1));
                os.push(1);
                break;
            default:
                if(ast.type.equals("boolean")) code.addIconst(ast.token.s.equals("true") ? 1 : 0);
                if(ast.token.s.equals("null")) code.add(Opcode.ACONST_NULL);
                os.push(1);
                break;
        }
    }

    private void compileArrayCreation(AST.ArrayCreation ast){
        int length = ast.calcs.length;

        if(CompilerUtil.isPrimitive(ast.type.substring(1))) {
            int atype = 0;
            switch (ast.type.substring(1)) {
                case "boolean":
                    atype = 4;
                    break;
                case "char":
                    atype = 5;
                    break;
                case "float":
                    atype = 6;
                    break;
                case "double":
                    atype = 7;
                    break;
                case "byte":
                    atype = 8;
                    break;
                case "short":
                    atype = 9;
                    break;
                case "int":
                    atype = 10;
                    break;
                case "long":
                    atype = 11;
                    break;
            }
            code.addNewarray(atype, length);
        }else code.addAnewarray(ast.type.substring(1));

        for(int i = 0;i < length;i++){
            code.add(Opcode.DUP);
            if(i < 6) code.addIconst(i);
            else code.add(Opcode.BIPUSH ,i);

            compileCalc(ast.calcs[i]);
            os.pop();

            switch(ast.type.substring(1)){
                case "int":
                case "boolean":
                case "char":
                case "byte":
                case "short":
                    code.add(Opcode.IASTORE);
                    break;
                case "float":
                    code.add(Opcode.FASTORE);
                    break;
                case "double":
                    code.add(Opcode.DASTORE);
                    break;
                case "long":
                    code.add(Opcode.LASTORE);
                    break;
                default:
                    code.add(Opcode.AASTORE);
                    break;
            }
        }

        os.push(1);
    }

    static MethodCompiler getInstance(){
        if(INSTANCE == null) INSTANCE = new MethodCompiler();

        return INSTANCE;
    }

    static MethodInfo compileMethod(KtjInterface clazz, String clazzName, ConstPool cp, KtjMethod method, String desc){
        String name = desc.split("%", 2)[0];
        StringBuilder descBuilder = new StringBuilder("(");

        for(KtjMethod.Parameter p:method.parameter) descBuilder.append(CompilerUtil.toDesc(p.type));

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
                getInstance().compileCode(code, (initValues != null ? initValues : "") + "\n"+method.code, clazz, clazzName, method, cp);
            }else getInstance().compileCode(code, method.code, clazz, clazzName, method, cp);

            if(code.getMaxLocals() == 0) code.setMaxLocals(code.getMaxLocals() + method.getLocals());

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
