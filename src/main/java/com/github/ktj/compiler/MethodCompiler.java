package com.github.ktj.compiler;

import com.github.ktj.lang.KtjClass;
import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

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
        int i = 0;

        while(i < ast.length){
            compileAST(ast[i]);

            i++;
        }
    }

    private void compileAST(AST ast){
        if(ast instanceof AST.If) compileIf((AST.If) ast);
        else if(ast instanceof AST.While) compileWhile((AST.While) ast);
        else if(ast instanceof AST.Return) compileReturn((AST.Return) ast);
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
        code.addIndex(-(code.getSize() - start));
        code.write16bit(branch, code.getSize() - branch + 1);
        os.clearScope(code);
    }

    private void compileIf(AST.If ast){

    }

    private void compileReturn(AST.Return ast){
        compileCalc(ast.calc);

        switch(ast.type){
            case "double" -> code.add(Opcode.DRETURN);
            case "float" -> code.add(Opcode.FRETURN);
            case "long" -> code.add(Opcode.LRETURN);
            case "int", "boolean", "short", "byte", "char" -> code.add(Opcode.IRETURN);
            default -> throw new RuntimeException("illegal argument");
        }
    }

    private void compileCast(AST.Value value){
        switch(value.cast){
            case "int", "short", "byte" -> {
                switch(value.type){
                    case "double" -> {
                        code.add(Opcode.I2D);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "long" -> {
                        code.add(Opcode.I2L);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "float" -> code.add(Opcode.I2F);
                    case "byte" -> code.add(Opcode.I2B);
                    case "char" -> code.add(Opcode.I2C);
                    case "short" -> code.add(Opcode.I2S);
                }
            }
            case "double" -> {
                switch(value.type){
                    case "int" -> {
                        code.add(Opcode.D2I);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                    case "long" -> code.add(Opcode.D2L);
                    case "float" -> {
                        code.add(Opcode.D2F);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                }
            }
            case "long" -> {
                switch(value.type){
                    case "double" -> code.add(Opcode.L2D);
                    case "int" -> {
                        code.add(Opcode.L2I);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                    case "float" -> {
                        code.add(Opcode.L2F);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                }
            }
            case "float" -> {
                switch(value.type){
                    case "double" -> {
                        code.add(Opcode.F2D);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "long" -> {
                        code.add(Opcode.F2L);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "int" -> code.add(Opcode.F2I);
                }
            }
        }
    }

    private void compileCalc(AST.Calc ast){
        if(ast.right == null) {
            if(ast.value.call != null)
                compileCall(ast.value.call);
            else
                addValue(ast.value);
        }else{
            if(ast.opp.equals("=") || ast.opp.equals("#")){
                compileCalc(ast.left, code, cp, os);
                if(ast.opp.equals("=")) code.add(Opcode.DUP);
                compileStore(ast.right.value.call.call, ast.type, ast.right.value.call.clazz);
                return;
            }

            compileCalc(ast.right, code, cp, os);

            if(ast.left == null) {
                if(ast.value.call != null)
                    compileCall(ast.value.call);
                else
                    addValue(ast.value);
            }else
                compileCalc(ast.left, code, cp, os);

            switch(ast.type){
                case "int", "char", "byte", "short", "boolean" -> {
                    switch (ast.opp){
                        case "+" -> code.add(Opcode.IADD);
                        case "-" -> code.add(Opcode.ISUB);
                        case "*" -> code.add(Opcode.IMUL);
                        case "/" -> code.add(Opcode.IDIV);
                        case "==", "!=", "<", "<=", ">", ">=" -> {
                            switch(ast.opp) {
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
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(ast.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "double" -> {
                    switch (ast.opp){
                        case "+" -> code.add(Opcode.DADD);
                        case "-" -> code.add(Opcode.DSUB);
                        case "*" -> code.add(Opcode.DMUL);
                        case "/" -> code.add(Opcode.DDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.DCMPG);
                            compileBoolOp(ast);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(ast.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "float" -> {
                    switch (ast.opp){
                        case "+" -> code.add(Opcode.FADD);
                        case "-" -> code.add(Opcode.FSUB);
                        case "*" -> code.add(Opcode.FMUL);
                        case "/" -> code.add(Opcode.FDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.FCMPG);
                            compileBoolOp(ast);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(ast.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "long" -> {
                    switch (ast.opp){
                        case "+" -> code.add(Opcode.LADD);
                        case "-" -> code.add(Opcode.LSUB);
                        case "*" -> code.add(Opcode.LMUL);
                        case "/" -> code.add(Opcode.LDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.LCMP);
                            compileBoolOp(ast);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(ast.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
            }
        }
    }

    private void compileCalc(AST.Calc calc, Bytecode code, ConstPool cp, OperandStack os){
        if(calc.right == null) {
            if(calc.value.call != null)
                compileCall(calc.value.call);
            else
                addValue(calc.value);
        }else{
            if(calc.opp.equals("=") || calc.opp.equals("#")){
                compileCalc(calc.left, code, cp, os);
                if(calc.opp.equals("=")) code.add(Opcode.DUP);
                compileStore(calc.right.value.call.call, calc.type, calc.right.value.call.clazz);
                return;
            }

            compileCalc(calc.right, code, cp, os);

            if(calc.left == null) {
                if(calc.value.call != null)
                    compileCall(calc.value.call);
                else
                    addValue(calc.value);
            }else
                compileCalc(calc.left, code, cp, os);

            switch(calc.type){
                case "int", "char", "byte", "short", "boolean" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.IADD);
                        case "-" -> code.add(Opcode.ISUB);
                        case "*" -> code.add(Opcode.IMUL);
                        case "/" -> code.add(Opcode.IDIV);
                        case "==", "!=", "<", "<=", ">", ">=" -> {
                            switch(calc.opp) {
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
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "double" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.DADD);
                        case "-" -> code.add(Opcode.DSUB);
                        case "*" -> code.add(Opcode.DMUL);
                        case "/" -> code.add(Opcode.DDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.DCMPG);
                            compileBoolOp(calc);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "float" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.FADD);
                        case "-" -> code.add(Opcode.FSUB);
                        case "*" -> code.add(Opcode.FMUL);
                        case "/" -> code.add(Opcode.FDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.FCMPG);
                            compileBoolOp(calc);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "long" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.LADD);
                        case "-" -> code.add(Opcode.LSUB);
                        case "*" -> code.add(Opcode.LMUL);
                        case "/" -> code.add(Opcode.LDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.LCMP);
                            compileBoolOp(calc);
                        }
                    }
                    os.pop();
                    if(CompilerUtil.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
            }
        }
    }

    private void compileBoolOp(AST.Calc calc) {
        switch(calc.opp){
            case "==", "!=" -> {
                code.addIconst(0);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<", ">" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(calc.opp.equals("<") ? "-1" : "1", Token.Type.INTEGER);
                addValue(value);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<=", ">=" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(calc.opp.equals("<=") ? "1" : "-1", Token.Type.INTEGER);
                addValue(value);
                code.addOpcode(Opcode.IF_ICMPNE);
            }
        }
        int branchLocation = code.getSize();
        code.addIndex(0);
        if(calc.opp.equals("!=")) code.addIconst(1);
        else code.addIconst(0);
        code.addOpcode(Opcode.GOTO);
        int endLocation = code.getSize();
        code.addIndex(0);
        code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
        if(calc.opp.equals("!=")) code.addIconst(0);
        else code.addIconst(1);
        code.write16bit(endLocation, code.getSize() - endLocation + 1);
    }

    private void addValue(AST.Value value){
        switch (value.token.t().toString()){
            case "int", "short", "byte", "char" -> {
                int intValue;
                if(value.type.equals("char"))
                    intValue = value.token.s().toCharArray()[1];
                else
                    intValue = Integer.parseInt(value.token.s());

                if(intValue < 6 && intValue >= 0)
                    code.addIconst(intValue);
                else
                    code.add(0x10 ,intValue); //Bipush

                os.push(1);
            }
            case "boolean" -> {
                code.addIconst(value.token.s().equals("true") ? 1 : 0);
                os.push("temp", 1);
            }
            case "float" -> {
                int index = 0;
                float floatValue = Float.parseFloat(value.token.s());
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
                double doubleValue = Double.parseDouble(value.token.s());
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
                long longValue = Long.parseLong(value.token.s());
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

        if(value.cast != null)
            compileCast(value);
    }

    private void compileCall(AST.Call call){
        if(call instanceof AST.StaticCall){

        }else compileLoad(call, code, os);
    }

    private void compileVarAssignment(AST.VarAssignment ast){
        if(ast.call == null) {
            compileCalc(ast.calc);
            compileStore(ast.name, ast.type, clazzName);
            int length = (ast.type.equals("double") || ast.type.equals("long")) ? 2 : 1;
            os.pop();
            os.push(ast.name, length);
        }else{

        }
    }

    private void compileLoad(AST.Call ast, Bytecode code, OperandStack os){
        if(ast.type == null)
            return; //TODO

        if(os.contains(ast.call)) {
            switch (ast.type) {
                case "int", "boolean", "char", "byte", "short" -> {
                    code.addIload(os.get(ast.call));
                    os.push(1);
                }
                case "float" -> {
                    code.addFload(os.get(ast.call));
                    os.push(1);
                }
                case "double" -> {
                    code.addDload(os.get(ast.call));
                    os.push(2);
                }
                case "long" -> {
                    code.addLload(os.get(ast.call));
                    os.push(2);
                }
            }
        }else{
            throw new RuntimeException(STR."Variable \{ast.call} did not exist");
        }
    }

    private void compileStore(String name, String type, String clazz){
        if(os.contains(name) || (this.clazz instanceof KtjClass && !((KtjClass) this.clazz).fields.containsKey(name))) {
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
        }else{
            if(!(this.clazz instanceof KtjClass) || ((KtjClass) this.clazz).fields.containsKey(name)) return;

            if(!((KtjClass) this.clazz).fields.get(name).modifier.statik) code.addPutfield(clazz, name, CompilerUtil.toDesc(type));
            else code.addPutstatic(clazz, name, CompilerUtil.toDesc(type));

            os.pop();
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
