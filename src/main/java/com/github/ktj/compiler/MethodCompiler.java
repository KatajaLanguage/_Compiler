package com.github.ktj.compiler;

import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

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
        if(ast instanceof AST.VarAssignment) compileVarAssignment((AST.VarAssignment) ast);
        else if(ast instanceof AST.While) compileWhile((AST.While) ast);
        else if(ast instanceof AST.If) compileIf((AST.If) ast);
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

    private void compileVarAssignment(AST.VarAssignment ast){
        compileCalc(ast.calc);
        compileStore(ast.name, ast.type);
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
                    code.add(0x10 ,intValue); //Bipush

                os.push(1);
            }
            case "boolean" -> {
                code.addIconst(ast.token.s().equals("true") ? 1 : 0);
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
        }
    }

    private void compileStore(String name, String type){
        int where = os.get(name);

        if(where == -1) {
            os.pop();
            where = os.push(name, Set.of("double", "long").contains(type) ? 2 : 1);
        }

        switch (type) {
            case "int", "boolean", "char", "byte", "short" -> code.addIstore(where);
            case "float" -> code.addFstore(where);
            case "double" -> code.addDstore(where);
            case "long" -> code.addLstore(where);
        }
    }

    static MethodCompiler getInstance(){
        if(INSTANCE == null) INSTANCE = new MethodCompiler();

        return INSTANCE;
    }

    static MethodInfo compileMethod(KtjInterface clazz, String clazzName, ConstPool cp, KtjMethod method, String desc){
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

            getInstance().compileCode(code, method.code, clazz, clazzName, method, cp);

            if(method.returnType.equals("void")) code.addReturn(null);

            mInfo.setCodeAttribute(code.toCodeAttribute());
        }

        return mInfo;
    }
}
