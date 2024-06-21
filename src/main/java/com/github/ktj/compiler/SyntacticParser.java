package com.github.ktj.compiler;

import com.github.ktj.lang.*;

import java.util.ArrayList;
import java.util.HashMap;

final class SyntacticParser {

    private static final class Scope{
        public final Scope last;
        private final HashMap<String, String> vars = new HashMap<>();

        Scope(Scope current){
            last = current;
        }

        Scope(String type, KtjMethod method){
            last = null;
            vars.put("this", type);
            vars.put("null", "java.lang.method");
            for(int i = 0;i < method.parameter.length;i++){
                if(vars.containsKey(method.parameter[i].name)) throw new RuntimeException(method.parameter[i].name+" is already defined at "+method.file+":"+method.line+1);
                vars.put(method.parameter[i].name, method.parameter[i].type);
            }
        }

        String getType(String name){
            if(vars.containsKey(name))
                return vars.get(name);

            return last == null ? null : last.getType(name);
        }

        void add(String name, String type){
            if(!vars.containsKey(name))
                vars.put(name, type);
        }
    }

    private TokenHandler th;
    private Compilable clazz;
    private KtjMethod method;
    private Scope scope;
    private String clazzName;
    private String[] code;
    private int index;

    AST[] parseAst(Compilable clazz, String clazzName, KtjMethod method, String code){
        if(code.trim().isEmpty()) return new AST[]{CompilerUtil.getDefaultReturn(method.returnType)};

        this.clazz = clazz;
        this.method = method;
        scope = new Scope(clazzName, method);
        this.clazzName = clazzName;
        this.code = code.split("\n");
        index = -1;

        ArrayList<AST> ast = new ArrayList<>();

        if(this.code.length == 0) return new AST[]{CompilerUtil.getDefaultReturn(method.returnType)};

        nextLine();

        while(hasNextStatement()){
            try {
                AST e = parseNextStatement();
                if(e != null) ast.add(e);
                else if(th.toStringNonMarked().startsWith("}")) throw new RuntimeException("illegal argument");
            }catch(RuntimeException e){
                RuntimeException exception = new RuntimeException(e.getMessage()+" at "+method.file+":"+method.line);
                exception.setStackTrace(e.getStackTrace());
                throw exception;
            }
        }

        if(ast.isEmpty() || !(ast.get(ast.size() - 1) instanceof AST.Return)) ast.add(CompilerUtil.getDefaultReturn(method.returnType));

        return ast.toArray(new AST[0]);
    }

    private AST parseNextStatement(){
        if(th.toStringNonMarked().startsWith("}") || !hasNextStatement()) return null;

        if(!th.hasNext()) nextLine();

        AST ast;

        switch(th.assertToken(Token.Type.IDENTIFIER).s){
            case "while":
                ast = parseWhile();
                break;
            case "if":
                ast = parseIf();
                break;
            case "return":
                ast = parseReturn();
                break;
            default:
                th.last();
                ast = parseStatement();

                if(ast instanceof AST.Load && (((AST.Load)(ast)).call == null || ((AST.Load)(ast)).call.argTypes == null)) throw new RuntimeException("not a statement");
                break;
        }

        th.isNext(";");
        return ast;
    }

    private AST.Return parseReturn(){
        AST.Return ast = new AST.Return();

        if(th.hasNext()){
            ast.calc = parseCalc();
            ast.type = ast.calc.type;
            assertEndOfStatement();
        }else ast.type = "void";

        if(!ast.type.equals(method.returnType)  && !(ast.type.equals("null") && !CompilerUtil.isPrimitive(method.returnType))) throw new RuntimeException("Expected type "+method.returnType+" got "+ast.type);

        return ast;
    }

    private AST.While parseWhile(){
        AST.While ast = new AST.While();

        th.assertHasNext();
        ast.condition = parseCalc();

        if(!ast.condition.type.equals("boolean")) throw new RuntimeException("Expected type boolean got "+ast.condition.type);

        if(th.assertToken("{", "->").equals("->")){
            ast.ast = new AST[]{parseNextStatement()};
        }else ast.ast = parseContent();

        return ast;
    }

    private AST.If parseIf(){
        AST.If ast = new AST.If();

        th.assertHasNext();
        ast.condition = parseCalc();
        if(!ast.condition.type.equals("boolean")) throw new RuntimeException("Expected type boolean got "+ast.condition.type);
        if(th.assertToken("->", "{").equals("->")){
            ast.ast = new AST[]{parseNextStatement()};
        }else{
            ast.ast = parseContent();

            AST.If current = ast;
            boolean end = false;
            while (th.hasNext() && !end) {
                current = (current.elif = new AST.If());
                th.assertToken("else");

                if (th.assertToken("if", "{").equals("if")) {
                    current.condition = parseCalc();
                    if (!current.condition.type.equals("boolean"))
                        throw new RuntimeException("Expected type boolean got " + current.condition.type);
                    th.assertToken("{");
                } else end = true;

                current.ast = parseContent();
            }

            if (!th.toStringNonMarked().equals("} ")) throw new RuntimeException("illegal argument");
        }

        return ast;
    }

    private AST[] parseContent(){
        scope = new Scope(scope);
        ArrayList<AST> astList = new ArrayList<>();

        while(!th.isNext("}")){
            AST current = parseNextStatement();
            if(current != null) astList.add(current);
        }

        if(!th.current().equals("}")) throw new RuntimeException("Expected }");

        scope = scope.last;
        return astList.toArray(new AST[0]);
    }

    private AST parseStatement(){
        if(!th.isNext(Token.Type.IDENTIFIER)) throw new RuntimeException("illegal argument");

        if(th.isNext(Token.Type.IDENTIFIER)){
            th.last();
            th.last();

            String type = th.assertToken(Token.Type.IDENTIFIER).s;
            String name = th.assertToken(Token.Type.IDENTIFIER).s;
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            assertEndOfStatement();

            if(method.uses.containsKey(type)){
                if(!CompilerUtil.classExist(method.uses.get(type))) throw new RuntimeException("Class "+method.uses.get(type)+" is not defined");
                type = method.uses.get(type);
            }

            if(!type.equals(calc.type) && !(calc.type.equals("null") && !CompilerUtil.isPrimitive(type))) throw new RuntimeException("Expected type "+type+" got "+calc.type);
            if(scope.getType(name) != null) throw new RuntimeException("variable "+name+" is already defined");

            scope.add(name, type);

            AST.VarAssignment ast = new AST.VarAssignment();

            ast.calc = calc;
            ast.type = type;
            ast.load = new AST.Load();
            ast.load.type = type;
            ast.load.name = name;
            ast.load.clazz = type;

            return ast;
        }else{
            th.last();
            AST.Load load = parseCall();

            if(load.finaly) assertEndOfStatement();

            if (th.hasNext()) {
                th.assertToken("=");
                AST.Calc calc = parseCalc();
                assertEndOfStatement();

                if(!load.type.equals(calc.type) && !(calc.type.equals("null") && !CompilerUtil.isPrimitive(load.type))) throw new RuntimeException("Expected type "+load.type+" got "+calc.type);

                AST.VarAssignment ast = new AST.VarAssignment();
                ast.calc = calc;
                ast.load = load;
                ast.type = ast.calc.type;

                return ast;
            } else return load;
        }
    }

    private AST.Calc parseCalc(){
        AST.Calc ast = new AST.Calc();

        ast.arg = parseValue();
        ast.type = ast.arg.type;

        while(th.hasNext()){
            if(!th.next().equals(Token.Type.OPERATOR) || th.current().equals("->")){
                th.last();
                return ast;
            }

            ast.setRight();
            ast.op = th.current().s;
            ast.arg = parseValue();
            ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.arg.type, ast.op);

            if(ast.type == null) throw new RuntimeException("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.arg.type);
        }

        return ast;
    }

    private AST.CalcArg parseValue(){
        th.next();

        if(th.isNext(Token.Type.IDENTIFIER)){
            AST.Cast ast = new AST.Cast();

            ast.cast = th.current().s;
            ast.calc = parseCalc();
            ast.type = ast.cast;

            if(!(CompilerUtil.isPrimitive(ast.cast) || (method.uses.containsKey(ast.cast) && CompilerUtil.classExist(method.uses.get(ast.cast))))) throw new RuntimeException("Type "+ast.cast+" is not defined");

            if(!CompilerUtil.canCast(ast.calc.type, ast.cast)) throw new RuntimeException("Unable to cast "+ast.calc.type+" to "+ast.cast);

            return ast;
        }

        AST.Value ast = new AST.Value();

        switch(th.current().t){
            case CHAR:
            case SHORT:
            case INTEGER:
            case LONG:
            case DOUBLE:
            case FLOAT:
                ast.token = th.current();
                ast.type = th.current().t.toString();
                break;
            case IDENTIFIER:
                if(th.current().equals("true") || th.current().equals("false")){
                    ast.token = th.current();
                    ast.type = "boolean";
                }else if(th.current().equals("null")){
                    ast.token = th.current();
                    ast.type = "null";
                }else{
                    th.last();
                    ast.load = parseCall();
                    ast.type = ast.load.type;
                }
                break;
            case STRING:
                ast.token = th.current();
                ast.type = "java.lang.String";
                break;
            default:
                throw new RuntimeException("illegal argument");
        }

        return ast;
    }

    private AST.Load parseCall(){
        AST.Load ast = new AST.Load();
        AST.Call current;

        String call = th.assertToken(Token.Type.IDENTIFIER).s;
        if(th.isNext("[")){
            if(!CompilerUtil.isPrimitive(call) && (!method.uses.containsKey(call) || !CompilerUtil.classExist(method.uses.get(call)))) throw new RuntimeException("Class "+method.uses.get(call)+" is not defined");

            ast.call = current = new AST.Call();
            current.call = "<init>";
            current.clazz = "["+(CompilerUtil.isPrimitive(call) ? call : method.uses.get(call));
            current.type = current.clazz;
            current.argTypes = new AST.Calc[]{parseCalc()};
            ast.finaly = false;
            ast.type = current.type;

            th.assertToken("]");
            if(!current.argTypes[0].type.equals("int")) throw new RuntimeException("Expected type int got "+current.argTypes[0].type);
        }else if(th.isNext("(")){
            if(!method.uses.containsKey(call)){
                StringBuilder desc = new StringBuilder(call);
                ArrayList<AST.Calc> args = new ArrayList<>();

                while (th.hasNext() && !th.next().equals(")")) {
                    th.last();
                    args.add(parseCalc());
                    th.assertToken(",", ")");
                }

                for (AST.Calc calc : args) desc.append("%").append(calc.type);

                ast.call = current = new AST.Call();

                if (CompilerUtil.getMethodReturnType(clazzName, desc.toString(), false, true) != null) {
                    current.statik = false;
                    current.type = CompilerUtil.getMethodReturnType(clazzName, desc.toString(), false, true);
                } else if (CompilerUtil.getMethodReturnType(clazzName, desc.toString(), true, true) != null) {
                    current.statik = true;
                    current.type = CompilerUtil.getMethodReturnType(clazzName, desc.toString(), true, true);
                } else
                    throw new RuntimeException("Method "+desc+" is not defined for class "+clazzName);

                current.clazz = clazzName;
                current.call = call;
                current.argTypes = args.toArray(new AST.Calc[0]);
                ast.finaly = true;
                ast.type = current.type;
            }else{
                if(!CompilerUtil.classExist(method.uses.get(call))) throw new RuntimeException("Class "+method.uses.get(call)+" is not defined");

                String type = method.uses.get(call);
                call = "<init>";
                StringBuilder desc = new StringBuilder(call);
                ArrayList<AST.Calc> args = new ArrayList<>();

                while (th.hasNext() && !th.next().equals(")")) {
                    th.last();
                    args.add(parseCalc());
                    th.assertToken(",", ")");
                }

                for (AST.Calc calc : args) desc.append("%").append(calc.type);

                if (CompilerUtil.getMethodReturnType(type, desc.toString(), false, type.equals(clazzName)) == null)
                    throw new RuntimeException("static Method "+desc+" is not defined for class "+type);

                ast.call = current = new AST.Call();
                current.type = type;
                current.argTypes = args.toArray(new AST.Calc[0]);
                ast.finaly = true;
                current.call = call;
                current.clazz = type;
                current.statik = false;
                ast.type = current.type;
            }
        }else{
            if(scope.getType(call) != null){
                ast.name = call;
                ast.type = scope.getType(call);
                ast.clazz = ast.type;
                current = null;
            }else if(method.uses.containsKey(call)) {
                if(!CompilerUtil.classExist(method.uses.get(call))) throw new RuntimeException("Class "+method.uses.get(call)+" is not defined");

                String type = method.uses.get(call);
                th.assertToken(".");
                call = th.assertToken(Token.Type.IDENTIFIER).s;

                if (th.isNext("(")) {
                    StringBuilder desc = new StringBuilder(call);
                    ArrayList<AST.Calc> args = new ArrayList<>();

                    while(th.hasNext() && !th.next().equals(")")){
                        th.last();
                        args.add(parseCalc());
                        th.assertToken(",", ")");
                    }

                    for(AST.Calc calc:args) desc.append("%").append(calc.type);

                    if(CompilerUtil.getMethodReturnType(type, desc.toString(), true, type.equals(clazzName)) == null) throw new RuntimeException("static Method "+ desc +" is not defined for class "+type);

                    ast.call = current = new AST.Call();
                    current.type = CompilerUtil.getMethodReturnType(type, desc.toString(), true, type.equals(clazzName));
                    current.argTypes = args.toArray(new AST.Calc[0]);
                    ast.finaly = true;
                }else{
                    if (CompilerUtil.getFieldType(type, call, true, type.equals(clazzName)) == null)
                        throw new RuntimeException("static Field "+call+" is not defined for class "+type);

                    ast.call = current = new AST.Call();
                    current.type = CompilerUtil.getFieldType(type, call, true, type.equals(clazzName));
                    ast.finaly = CompilerUtil.isFinal(type, call);
                }

                current.call = call;
                current.clazz = type;
                current.statik = true;
                ast.type = current.type;
            }else{
                if(CompilerUtil.getFieldType(clazzName, call, true, true) != null){
                    ast.call = current = new AST.Call();
                    current.type = CompilerUtil.getFieldType(clazzName, call, false, true);
                    current.clazz = clazzName;
                    current.call = call;
                    current.statik = true;
                    ast.call = current = current.toStatic();
                    ast.type = current.type;
                }else if(CompilerUtil.getFieldType(clazzName, call, false, true) != null){
                    ast.call = current = new AST.Call();
                    current.type = CompilerUtil.getFieldType(clazzName, call, false, true);
                    current.clazz = clazzName;
                    current.call = call;
                    ast.type = current.type;
                }else throw new RuntimeException("Field "+call+" is not defined");

                ast.finaly = CompilerUtil.isFinal(clazzName, call);
            }
        }

        while(th.hasNext()) {
            if (!th.next().equals(".") && !th.current().equals("[")) {
                th.last();
                break;
            }

            String currentType;

            if (current == null) {
                current = new AST.Call();
                ast.call = current;
                currentType = ast.type;
            } else {
                currentType = current.type;
                current.setPrev();
            }

            if(th.current().equals("[")){
                current.statik = false;
                current.type = currentType.substring(1);
                current.clazz = currentType;
                current.argTypes = new AST.Calc[]{parseCalc()};

                th.assertToken("]");

                if(!current.argTypes[0].type.equals("int")) throw new RuntimeException("Expected type int got "+current.argTypes[0].type);
            }else{
                current.call = th.assertToken(Token.Type.IDENTIFIER).s;

                if (th.isNext("(")) {
                    StringBuilder desc = new StringBuilder(current.call);
                    ArrayList<AST.Calc> args = new ArrayList<>();

                    while (th.hasNext() && !th.next().equals(")")) {
                        th.last();
                        args.add(parseCalc());
                        th.assertToken(",", ")");
                    }

                    for (AST.Calc calc : args) desc.append("%").append(calc.type);

                    if (CompilerUtil.getMethodReturnType(currentType, desc.toString(), false, currentType.equals(clazzName)) == null)
                        throw new RuntimeException("Method "+ desc +" is not defined for class "+currentType);

                    current.type = CompilerUtil.getMethodReturnType(currentType, desc.toString(), false, currentType.equals(clazzName));
                    current.clazz = currentType;
                    current.argTypes = args.toArray(new AST.Calc[0]);
                    current.statik = false;
                    ast.finaly = true;
                } else {
                    current.type = CompilerUtil.getFieldType(currentType, current.call, false, currentType.equals(clazzName));

                    if (current.type == null)
                        throw new RuntimeException("Field "+current.call+" is not defined for class "+currentType);

                    current.clazz = currentType;
                    current.argTypes = null;
                    current.statik = false;
                    ast.finaly = CompilerUtil.isFinal(currentType, call);
                }
            }

            ast.type = current.type;
        }

        return ast;
    }

    private void assertEndOfStatement(){
        if(!th.isNext(";")) th.assertNull();
    }

    private boolean hasNextStatement(){
        return th.hasNext() || hasNextLine();
    }

    private void nextLine(){
        clazz.line++;
        index++;
        th = Lexer.lex(code[index]);
    }

    private boolean hasNextLine(){
        return index < code.length - 1;
    }
}
