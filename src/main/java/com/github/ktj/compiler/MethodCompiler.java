package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.KtjClass;
import com.github.ktj.lang.KtjInterface;
import com.github.ktj.lang.KtjMethod;
import com.github.ktj.lang.Modifier;
import javassist.bytecode.*;

import java.util.ArrayList;

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
        this.clazzName = clazzName;
        os = OperandStack.forMethod(method);
        AST[] ast = parser.parseAst(clazz, clazzName, method, ktjCode);

        for (AST value : ast) compileAST(value);
    }

    private ArrayList<Integer> compileAST(AST ast){
        if(ast instanceof AST.While) compileWhile((AST.While) ast);
        else if(ast instanceof AST.For) compileFor((AST.For) ast);
        else if(ast instanceof AST.If) return compileIf((AST.If) ast);
        else if(ast instanceof AST.Return) compileReturn((AST.Return) ast);
        else if(ast instanceof AST.Throw) compileThrow((AST.Throw) ast);
        else if(ast instanceof AST.TryCatch) return compileTryCatch((AST.TryCatch) ast);
        else if(ast instanceof AST.Switch) compileSwitch((AST.Switch) ast);
        else if(ast instanceof AST.Load){
            compileCall((AST.Load) ast, true);
            if(!ast.type.equals("void"))
                code.add(Opcode.POP);
        }else if(ast instanceof AST.VarAssignment) compileVarAssignment((AST.VarAssignment) ast, false);
        else if(ast instanceof AST.Calc) compileCalc((AST.Calc) ast, true);

        return new ArrayList<>();
    }

    private void compileSwitch(AST.Switch ast){
        compileCalc(ast.calc, false);
        if(!CompilerUtil.isPrimitive(ast.type)){
            if(ast.type.equals("java.lang.String")){
                //int pos = os.push("&temp", 1);
                //code.addAstore(pos);
                //code.addIconst(-1);
                //os.push("&temp", 1);
                //code.addIstore(pos + 1);
                //code.addAload(pos);
                code.addInvokevirtual("java.lang.String", "hashCode", "()I");
            }else code.addInvokevirtual(ast.type, "ordinal", "()I");
        }

        int start = code.getSize();

        code.add(Opcode.LOOKUPSWITCH);

        while(code.getSize()%4 != 0) code.add(Opcode.NOP);

        int defauld = code.getSize();
        code.addGap(8);
        code.write32bit(defauld, 0);
        code.write32bit(defauld + 4, ast.values.size());

        //cases
        for(Token t:ast.values.keySet()){
            int size = code.getSize();
            code.addGap(8);
            code.write32bit(size, parseSwitchValue(t, ast.type));
            code.write32bit(size + 4, 0);
        }

        ArrayList<Integer> ends = new ArrayList<>();
        ArrayList<Integer> starts = new ArrayList<>();

        for(AST[] asts:ast.branches){
            os.newScope();
            starts.add(code.getSize());

            for(AST a : asts){
                if(a instanceof AST.Break){
                    code.add(Opcode.GOTO);
                    ends.add(code.getSize());
                    code.addIndex(0);
                }else ends.addAll(compileAST(a));
            }

            if(asts.length == 0 || !(asts[asts.length - 1] instanceof AST.Return)){
                code.add(Opcode.GOTO);
                ends.add(code.getSize());
                code.addIndex(0);
            }
            os.clearScope(code);
        }

        int i = 1;
        for(Token t:ast.values.keySet()){
            code.write32bit(defauld + 4 + (i * 8), starts.get(ast.values.get(t)) - start);
            i++;
        }

        //default
        code.write32bit(defauld, code.getSize() - start);

        if(ast.defauld != null) for(AST a:ast.defauld){
            os.newScope();
            if(a instanceof AST.Break){
                code.add(Opcode.GOTO);
                ends.add(code.getSize());
                code.addIndex(0);
            }else ends.addAll(compileAST(a));
            os.clearScope(code);
        }

        if(!ends.isEmpty()) for(int end:ends) code.write16bit(end, code.getSize() - end + 1);
    }

    private int parseSwitchValue(Token token, String type){
        switch(token.t){
            case SHORT:
            case INTEGER:
                return Integer.parseInt(token.s);
            case CHAR:
                return token.s.toCharArray()[0];
            case IDENTIFIER:
                return CompilerUtil.getEnumOrdinal(type, token.s);
            case STRING:
                return token.s.hashCode();
        }
        return 0;
    }

    private void compileThrow(AST.Throw ast){
        compileCalc(ast.calc, false);
        code.add(Opcode.ATHROW);
    }

    private ArrayList<Integer> compileTryCatch(AST.TryCatch ast){
        ArrayList<Integer> breaks = new ArrayList<>();

        int start = code.getSize();
        os.newScope();
        for(AST statement: ast.tryAST){
            if(statement instanceof AST.Break){
                code.add(Opcode.GOTO);
                breaks.add(code.getSize());
                code.addIndex(0);
            }else breaks.addAll(compileAST(statement));
        }
        os.clearScope(code);
        int end = code.getSize();

        os.newScope();
        code.addAstore(os.push(ast.variable, 1));
        for(AST statement: ast.catchAST){
            if(statement instanceof AST.Break){
                code.add(Opcode.GOTO);
                breaks.add(code.getSize());
                code.addIndex(0);
            }else breaks.addAll(compileAST(statement));
        }
        os.clearScope(code);

        code.addExceptionHandler(start, end, end, ast.type);
        return breaks;
    }

    private void compileReturn(AST.Return ast){
        if(ast.calc != null) compileCalc(ast.calc, false);

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

    private void compileFor(AST.For ast){
        ArrayList<Integer> breaks = new ArrayList<>();
        os.newScope();
        int loop = -1;

        if(ast.from != null){
            AST.Value value = new AST.Value();
            value.token = ast.from;
            value.type = ast.type;
            compileValue(value);
            int where;
            switch(ast.type){
                case "short":
                case "int":
                    where = os.push(ast.variable, 1);
                    code.addIstore(where);
                    loop = code.getSize();
                    code.addIload(where);
                    compileCalc(ast.to, false);
                    int stepI = Integer.parseInt(ast.step.s) - Integer.parseInt(ast.from.s);
                    if(stepI < 0) code.add(Opcode.IF_ICMPLE);
                    else code.add(Opcode.IF_ICMPGE);
                    breaks.add(code.getSize());
                    code.addIndex(0);
                    break;
                case "double":
                    where = os.push(ast.variable, 2);
                    code.addDstore(where);
                    loop = code.getSize();
                    code.addDload(where);
                    compileCalc(ast.to, false);
                    double stepD = Double.parseDouble(ast.step.s) - Double.parseDouble(ast.from.s);
                    if(stepD < 0) code.add(Opcode.DCMPL);
                    else code.add(Opcode.DCMPG);
                    breaks.add(code.getSize());
                    code.addIndex(0);
                    break;
                case "float":
                    where = os.push(ast.variable, 1);
                    code.addFstore(where);
                    loop = code.getSize();
                    code.addFload(where);
                    compileCalc(ast.to, false);
                    float stepF = Float.parseFloat(ast.step.s) - Float.parseFloat(ast.from.s);
                    if(stepF < 0) code.add(Opcode.FCMPL);
                    else code.add(Opcode.FCMPG);
                    breaks.add(code.getSize());
                    code.addIndex(0);
                    break;
            }
        }else {
            compileCall(ast.load, true);
            if (ast.type.startsWith("[")) {
                int where = os.push("&i", 1);
                code.addAstore(where);
                code.addAload(where);
                code.add(Opcode.ARRAYLENGTH);
                os.push("&temp1", 1);
                code.addIstore(where + 1);
                code.addIconst(0);
                os.push("&temp2", 1);
                code.addIstore(where + 2);
                loop = code.getSize();
                code.addIload(where + 2);
                code.addIload(where + 1);
                code.add(Opcode.IF_ICMPGE);
                breaks.add(code.getSize());
                code.addIndex(0);
                code.addAload(where);
                code.addIload(where + 2);
                switch (ast.type) {
                    case "[int":
                    case "[short":
                    case "[char":
                    case "[boolean":
                    case "[byte":
                        code.add(Opcode.IALOAD);
                        os.push(ast.variable, 1);
                        code.addIstore(where + 3);
                        break;
                    case "[double":
                        code.add(Opcode.DALOAD);
                        os.push(ast.variable, 2);
                        code.addIstore(where + 3);
                        break;
                    case "[float":
                        code.add(Opcode.FALOAD);
                        os.push(ast.variable, 1);
                        code.addIstore(where + 3);
                        break;
                    case "[long":
                        code.add(Opcode.LALOAD);
                        os.push(ast.variable, 2);
                        code.addIstore(where + 3);
                        break;
                    default:
                        code.add(Opcode.AALOAD);
                        os.push(ast.variable, 1);
                        code.addIstore(where + 3);
                        break;
                }
            } else {
                code.addInvokevirtual(ast.load.type, "iterator", "()Ljava.util.Iterator;");
                int where = os.push("&i", 1);
                os.push(ast.variable, 1);
                code.addAstore(where);
                loop = code.getSize();
                code.addAload(where);
                code.addInvokeinterface("java.util.Iterator", "hasNext", "()Z", 1);
                code.add(Opcode.IFEQ);
                breaks.add(code.getSize());
                code.addIndex(0);
                code.addAload(where);
                code.addInvokeinterface("java.util.Iterator", "next", "()Ljava/lang/Object;", 1);
                os.push("&temp", 1);
                code.addAstore(where + 1);
            }
        }

        for(AST statement: ast.ast){
            if(statement instanceof AST.Break){
                code.add(Opcode.GOTO);
                breaks.add(code.getSize());
                code.addIndex(0);
            }else breaks.addAll(compileAST(statement));
        }

        if(ast.type.startsWith("[")){
            code.addIload(os.get("&i") + 2);
            code.addIconst(1);
            code.add(Opcode.IADD);
            code.addIstore(os.get("&i") + 2);
        }else if(ast.from != null){
            int where = os.get(ast.variable);
            switch(ast.type){
                case "short":
                case "int":
                    code.addIload(where);
                    code.addIconst(1);
                    code.add(Opcode.IADD);
                    code.addIstore(where);
                    break;
                case "long":
                    code.addLload(where);
                    code.addLconst(1);
                    code.add(Opcode.LADD);
                    code.addLstore(where);
                    break;
                case "double":
                    code.addDload(where);
                    code.addDconst(1);
                    code.add(Opcode.DADD);
                    code.addDstore(where);
                    break;
                case "float":
                    code.addFload(where);
                    code.addFconst(1);
                    code.add(Opcode.FADD);
                    code.addFstore(where);
                    break;
            }
        }

        code.add(Opcode.GOTO);
        code.addIndex(-(code.getSize() - loop) + 1);
        for(int branch:breaks) code.write16bit(branch, code.getSize() - branch + 1);
        os.clearScope(code);
    }

    private void compileWhile(AST.While ast){
        ArrayList<Integer> breaks = new ArrayList<>();

        os.newScope();
        int start = code.getSize();
        compileCalc(ast.condition, false);
        code.addOpcode(Opcode.IFEQ);
        breaks.add(code.getSize());
        code.addIndex(0);

        for(AST statement: ast.ast){
            if(statement instanceof AST.Break){
                code.add(Opcode.GOTO);
                breaks.add(code.getSize());
                code.addIndex(0);
            }else breaks.addAll(compileAST(statement));
        }

        code.addOpcode(Opcode.GOTO);
        code.addIndex(-(code.getSize() - start) + 1);
        for(int branch:breaks) code.write16bit(branch, code.getSize() - branch + 1);
        os.clearScope(code);
    }

    private ArrayList<Integer> compileIf(AST.If ast){
        ArrayList<Integer> gotos = new ArrayList<>();
        ArrayList<Integer> breaks = new ArrayList<>();
        int branch = 0;

        while (ast != null){
            os.newScope();
            if(ast.condition != null) {
                compileCalc(ast.condition, false);
                code.addOpcode(Opcode.IFEQ);
                branch = code.getSize();
                code.addIndex(0);
            }

            for(AST statement:ast.ast) {
                if(statement instanceof AST.Break){
                    code.add(Opcode.GOTO);
                    breaks.add(code.getSize());
                    code.addIndex(0);
                }else breaks.addAll(compileAST(statement));
            }

            if(ast.elif != null){
                code.addOpcode(Opcode.GOTO);
                gotos.add(code.getSize());
                code.addIndex(0);
            }

            if(ast.condition != null)
                code.write16bit(branch, code.getSize() - branch + 1);

            os.clearScope(code);
            ast = ast.elif;
        }

        for(int i:gotos)
            code.write16bit(i, code.getSize() - i + 1);

        return breaks;
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
                    break;
                case "float":
                    code.addFload(os.get(ast.name));
                    break;
                case "double":
                    code.addDload(os.get(ast.name));
                    break;
                case "long":
                    code.addLload(os.get(ast.name));
                    break;
                default:
                    code.addAload(os.get(ast.name));
                    break;
            }
            first = false;
        }

        if(!all && ast.name == null && ast.call.prev == null) return;

        if(ast.call != null) compileCall(all ? ast.call : ast.call.prev, first);
    }

    private void compileCall(AST.Call call, boolean first){
        if(call == null) return;

        if(call.prev != null){
            compileCall(call.prev, first);
            first = false;
        }

        if(call.argTypes == null) {
            if(call.clazz.startsWith("[")) code.add(Opcode.ARRAYLENGTH);
            else if (call.statik) code.addGetstatic(call.clazz, call.name, CompilerUtil.toDesc(call.type));
            else {
                if (first && call.clazz.equals(clazzName)) code.addAload(0);
                code.addGetfield(call.clazz, call.name, CompilerUtil.toDesc(call.type));
            }
        }else{
            if(!call.statik && first && call.clazz.equals(clazzName) && !call.name.equals("<init>")) code.addAload(0);

            if(!call.name.equals("<init>") || call.type.startsWith("[")){
                for(AST.Calc calc:call.argTypes) compileCalc(calc, false);
            }

            if(call.clazz.startsWith("[")){
                if(call.name.equals("<init>")){
                    if(call.clazz.startsWith("[[")) code.addMultiNewarray(CompilerUtil.toDesc(call.clazz), call.clazz.lastIndexOf("[") + 1);
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
            }else if(call.name.equals("<init>")){
                code.addNew(call.clazz);
                code.add(Opcode.DUP);
                for(AST.Calc calc:call.argTypes) compileCalc(calc, false);
                code.addInvokespecial(call.clazz, "<init>", CompilerUtil.signatureToDesc(call.signature, "void"));
            }else if(call.statik) code.addInvokestatic(call.clazz, call.name, CompilerUtil.signatureToDesc(call.signature, call.type));
            else code.addInvokevirtual(call.clazz, call.name, CompilerUtil.signatureToDesc(call.signature, call.type));
        }
    }

    private void compileVarAssignment(AST.VarAssignment ast, boolean dup){
        if(ast.load.name == null && ast.load.call != null && !ast.load.call.statik && ast.load.call.clazz.equals(clazzName)) code.addAload(0);
        compileCall(ast.load, false);

        if(ast.load.call == null){
            compileCalc(ast.calc, false);
            if(dup) code.add(Opcode.DUP);

            int where = os.get(ast.load.name);

            if(where == -1) {
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
            if(ast.load.call.clazz.startsWith("[")){
                compileCalc(ast.load.call.argTypes[0], false);
                compileCalc(ast.calc, false); //TODO

                switch(ast.load.call.clazz){
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
            }else if(ast.load.call.statik){
                compileCalc(ast.calc, false);
                if(dup) code.add(Opcode.DUP);
                code.addPutstatic(ast.load.call.clazz, ast.load.call.name, CompilerUtil.toDesc(ast.load.call.type));
            }else{
                compileCalc(ast.calc, false);
                if(dup) code.add(Opcode.DUP);
                code.addPutfield(ast.load.call.clazz, ast.load.call.name, CompilerUtil.toDesc(ast.load.call.type));
            }
        }
    }

    private void compileCalc(AST.Calc ast, boolean isStatement){
        if(ast.op != null && ast.op.equals("=")){
            if(ast.right.right != null) compileCalc(ast.right.right, false);

            assert ast.right.arg instanceof AST.Value;

            AST.VarAssignment varAssignment = new AST.VarAssignment();
            varAssignment.calc = ast.left;
            varAssignment.load = ((AST.Value) ast.right.arg).load;
            varAssignment.type = ast.type;
            compileVarAssignment(varAssignment, !isStatement);

            if(ast.right.right != null) compileOperator(ast);
            return;
        }else if(ast.op != null && ast.op.equals("+") && ast.right.type.equals("java.lang.String")){
            code.addNew("java.lang.StringBuilder");
            code.add(Opcode.DUP);
            code.addInvokespecial("java.lang.StringBuilder", "<init>", "()V");
            compileCalc(ast.right, false);
            code.addInvokevirtual("java.lang.StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            if(ast.left == null){
                if (ast.arg instanceof AST.Cast) compileCast((AST.Cast) ast.arg);
                else if (ast.arg instanceof AST.ArrayCreation) compileArrayCreation((AST.ArrayCreation) ast.arg);
                else if(ast.arg instanceof AST.InlineIf) compileInlineIf((AST.InlineIf) ast.arg);
                else compileValue((AST.Value) ast.arg);
            }else compileCalc(ast.left, false);
            code.addInvokevirtual("java.lang.StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            code.addInvokevirtual("java.lang.StringBuilder", "toString", "()Ljava/lang/String;");
            return;
        }

        if(ast.right != null) compileCalc(ast.right, false);

        if(ast.op != null){
            if(ast.op.equals(">>") && !CompilerUtil.isPrimitive(ast.right.type)){
                code.addInstanceof(((AST.Value) ast.arg).token.s);
                return;
            }else if(ast.op.equals("&&") && ((ast.left != null && ast.left.type.equals("boolean")) || ast.arg.type.equals("boolean"))){
                code.add(Opcode.IFEQ);
                int branchLocation1 = code.getSize();
                code.addIndex(0);
                if(ast.left == null){
                    if (ast.arg instanceof AST.Cast) compileCast((AST.Cast) ast.arg);
                    else if (ast.arg instanceof AST.ArrayCreation) compileArrayCreation((AST.ArrayCreation) ast.arg);
                    else if(ast.arg instanceof AST.InlineIf) compileInlineIf((AST.InlineIf) ast.arg);
                    else compileValue((AST.Value) ast.arg);
                }else compileCalc(ast.left, false);
                code.add(Opcode.IFEQ);
                int branchLocation2 = code.getSize();
                code.addIndex(0);
                code.add(Opcode.ICONST_1);
                code.add(Opcode.GOTO);
                int endLocation = code.getSize();
                code.addIndex(0);
                code.write16bit(branchLocation1, code.getSize() - branchLocation1 + 1);
                code.write16bit(branchLocation2, code.getSize() - branchLocation2 + 1);
                code.add(Opcode.ICONST_0);
                code.write16bit(endLocation, code.getSize() - endLocation + 1);
                return;
            }else if(ast.op.equals("||") && ((ast.left != null && ast.left.type.equals("boolean")) || ast.arg.type.equals("boolean"))){
                code.add(Opcode.IFNE);
                int branchLocation1 = code.getSize();
                code.addIndex(0);
                if(ast.left == null){
                    if (ast.arg instanceof AST.Cast) compileCast((AST.Cast) ast.arg);
                    else if (ast.arg instanceof AST.ArrayCreation) compileArrayCreation((AST.ArrayCreation) ast.arg);
                    else if(ast.arg instanceof AST.InlineIf) compileInlineIf((AST.InlineIf) ast.arg);
                    else compileValue((AST.Value) ast.arg);
                }else compileCalc(ast.left, false);
                code.add(Opcode.IFEQ);
                int branchLocation2 = code.getSize();
                code.addIndex(0);
                code.write16bit(branchLocation1, code.getSize() - branchLocation1 + 1);
                code.add(Opcode.ICONST_1);
                code.add(Opcode.GOTO);
                int endLocation = code.getSize();
                code.addIndex(0);
                code.write16bit(branchLocation2, code.getSize() - branchLocation2 + 1);
                code.add(Opcode.ICONST_0);
                code.write16bit(endLocation, code.getSize() - endLocation + 1);
                return;
            }
        }

        if(ast.left == null){
            if (ast.arg instanceof AST.Cast) compileCast((AST.Cast) ast.arg);
            else if (ast.arg instanceof AST.ArrayCreation) compileArrayCreation((AST.ArrayCreation) ast.arg);
            else if(ast.arg instanceof AST.InlineIf) compileInlineIf((AST.InlineIf) ast.arg);
            else compileValue((AST.Value) ast.arg);
        }else compileCalc(ast.left, false);
        if(ast.op != null) compileOperator(ast);
    }

    private void compileInlineIf(AST.InlineIf ast){
        compileCalc(ast.condition, false);
        code.add(Opcode.IFEQ);
        int start = code.getSize();
        code.addIndex(0);
        compileCalc(ast.trueValue, false);
        code.add(Opcode.GOTO);
        int end = code.getSize();
        code.addIndex(0);
        code.write16bit(start, code.getSize() - start + 1);
        compileCalc(ast.falseValue, false);
        code.write16bit(end, code.getSize() - end + 1);
    }

    private void compileCast(AST.Cast ast){
        compileCalc(ast.calc, false);

        switch(ast.calc.type){
            case "int":
            case "short":
            case "byte":
                switch(ast.type){
                    case "double":
                        code.add(Opcode.I2D);
                        break;
                    case "long":
                        code.add(Opcode.I2L);
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
                        break;
                    case "long":
                        code.add(Opcode.D2L);
                        break;
                    case "float":
                        code.add(Opcode.D2F);
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
                        break;
                    case "float":
                        code.add(Opcode.L2F);
                        break;
                }
                break;
            case "float":
                switch(ast.type){
                    case "double":
                        code.add(Opcode.F2D);
                        break;
                    case "long":
                        code.add(Opcode.F2L);
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
        switch(ast.right.type){
            case "int":
            case "char":
            case "byte":
            case "short":
            case "boolean":
                switch (ast.op){
                    case "+":
                        code.add(Opcode.IADD);
                        break;
                    case "-":
                        code.add(Opcode.ISUB);
                        break;
                    case "*":
                        code.add(Opcode.IMUL);
                        break;
                    case "/" :
                        code.add(Opcode.IDIV);
                        break;
                    case "&":
                        code.add(Opcode.IAND);
                        break;
                    case "|":
                        code.add(Opcode.IOR);
                        break;
                    case ">>":
                        code.add(Opcode.ISHR);
                        break;
                    case "<<":
                        code.add(Opcode.ISHL);
                        break;
                    case "^":
                        code.add(Opcode.IXOR);
                        break;
                    case "%":
                        code.add(Opcode.IREM);
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
                    case "%":
                        code.add(Opcode.DREM);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.DCMPG);
                        compileBoolOp(ast);
                        break;
                }
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
                    case "%":
                        code.add(Opcode.FREM);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.FCMPG);
                        compileBoolOp(ast);
                        break;
                }
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
                    case "&":
                        code.add(Opcode.LAND);
                        break;
                    case "|":
                        code.add(Opcode.LOR);
                        break;
                    case ">>":
                        code.add(Opcode.LSHR);
                        break;
                    case "<<":
                        code.add(Opcode.LSHL);
                        break;
                    case "^":
                        code.add(Opcode.LXOR);
                        break;
                    case "%":
                        code.add(Opcode.LREM);
                        break;
                    case "==":
                    case "!=":
                    case "<=":
                    case "<":
                    case ">=":
                    case ">":
                        code.add(Opcode.LCMP);
                        compileBoolOp(ast);
                        break;
                }
                break;
            default:
                if(ast.op.equals("===") || ast.op.equals("!==")){
                    if (ast.op.equals("===")) code.add(Opcode.IF_ACMPEQ);
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
                }else{
                    code.addInvokevirtual(ast.right.type, CompilerUtil.operatorToIdentifier(ast.op), "("+CompilerUtil.toDesc(ast.arg.type)+")"+CompilerUtil.toDesc(ast.type));
                }
                break;
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
        String type;

        if(ast.load != null){
            compileCall(ast.load, true);
            type = ast.load.type;
        }else{
            int index;
            type = ast.token.t.toString();

            switch (ast.token.t.toString()) {
                case "int":
                case "short":
                case "byte":
                case "char":
                    int intValue;
                    if (ast.type.equals("char"))
                        intValue = ast.token.s.toCharArray()[1];
                    else
                        intValue = Integer.parseInt(ast.token.s);

                    if (intValue < 6 && intValue >= 0)
                        code.addIconst(intValue);
                    else
                        code.add(Opcode.BIPUSH, intValue);

                    break;
                case "float":
                    index = 0;
                    float floatValue = Float.parseFloat(ast.token.s);
                    cp.addFloatInfo(floatValue);
                    while (index < cp.getSize()) {
                        try {
                            if (cp.getFloatInfo(index) == floatValue) break;
                            else index++;
                        } catch (Exception ignored) {
                            index++;
                        }
                    }
                    code.addLdc(index);
                    break;
                case "double":
                    index = 0;
                    double doubleValue = Double.parseDouble(ast.token.s);
                    cp.addDoubleInfo(doubleValue);
                    while (index < cp.getSize()) {
                        try {
                            if (cp.getDoubleInfo(index) == doubleValue) break;
                            else index++;
                        } catch (Exception ignored) {
                            index++;
                        }
                    }
                    code.addLdc(index);
                    break;
                case "long":
                    index = 0;
                    long longValue = Long.parseLong(ast.token.s);
                    cp.addLongInfo(longValue);
                    while (index < cp.getSize()) {
                        try {
                            if (cp.getLongInfo(index) == longValue) break;
                            else index++;
                        } catch (Exception ignored) {
                            index++;
                        }
                    }
                    code.addLdc(index);
                    break;
                case "java.lang.String":
                    code.addLdc(ast.token.s.substring(1, ast.token.s.length() - 1));
                    break;
                default:
                    if (ast.type.equals("boolean")) code.addIconst(ast.token.s.equals("true") ? 1 : 0);
                    if (ast.token.s.equals("null")) code.add(Opcode.ACONST_NULL);
                    break;
            }
        }

        if(ast.op != null)
            code.addInvokevirtual(type, CompilerUtil.operatorToIdentifier(ast.op), "()"+CompilerUtil.toDesc(ast.type));
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
        }else{
            AST.Calc calc = new AST.Calc();
            calc.type = "int";
            calc.arg = new AST.Value();
            calc.arg.type = "int";
            ((AST.Value)(calc.arg)).token = new Token(String.valueOf(length), Token.Type.INTEGER);
            compileCalc(calc, false);
            code.addAnewarray(CompilerUtil.toDesc(ast.type.substring(1)));
        }

        for(int i = 0;i < length;i++){
            code.add(Opcode.DUP);
            if(i < 6) code.addIconst(i);
            else code.add(Opcode.BIPUSH ,i);

            compileCalc(ast.calcs[i], false);

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

            code.setMaxLocals(code.getMaxLocals() + method.getLocals() + 5);
            code.setMaxStack(code.getMaxStack() * 2 + 5);

            mInfo.setCodeAttribute(code.toCodeAttribute());
        }

        return mInfo;
    }

    static MethodInfo compileClinit(KtjInterface clazz, String clazzName, ConstPool cp, String code){
        MethodInfo mInfo = new MethodInfo(cp, "<clinit>", "()V");
        mInfo.setAccessFlags(AccessFlag.STATIC);

        Bytecode bytecode = new Bytecode(cp);

        Modifier mod = new Modifier(AccessFlag.ACC_PRIVATE);
        mod.statik = true;

        getInstance().compileCode(bytecode, code, clazz, clazzName, new KtjMethod(mod, "void", code, new KtjMethod.Parameter[0], clazz.uses, clazz.statics, clazz.file, Integer.MIN_VALUE), cp);

        mInfo.setCodeAttribute(bytecode.toCodeAttribute());

        return mInfo;
    }
}
