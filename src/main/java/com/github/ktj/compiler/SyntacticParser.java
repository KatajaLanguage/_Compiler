package com.github.ktj.compiler;

import com.github.ktj.lang.*;

import java.util.ArrayList;
import java.util.HashMap;

final class SyntacticParser {

    private static final class Scope{
        public final Scope last;
        private final HashMap<String, String> vars = new HashMap<>();
        private final ArrayList<String> constants = new ArrayList<>();

        Scope(Scope current){
            last = current;
        }

        Scope(String type, KtjMethod method){
            last = null;
            vars.put("this", type);
            vars.put("null", "java.lang.Object");
            for(int i = 0;i < method.parameter.length;i++){
                if(vars.containsKey(method.parameter[i].name)) throw new RuntimeException(method.parameter[i].name+" is already defined at "+method.file+":"+method.line);
                vars.put(method.parameter[i].name, method.parameter[i].type);
            }
        }

        String getType(String name){
            if(vars.containsKey(name))
                return vars.get(name);

            return last == null ? null : last.getType(name);
        }

        boolean isConst(String name){
            return constants.contains(name);
        }

        void add(String name, String type, boolean constant){
            if(!vars.containsKey(name))
                vars.put(name, type);
            if(constant)
                constants.add(name);
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
                else if(th.isNext("}")) throw new RuntimeException("Illegal argument");
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
        if(!hasNextStatement()) return null;

        while((!th.hasNext() || th.isEmpty()) && hasNextLine()) nextLine();

        if(th.isNext("}")){
            th.last();
            return null;
        }

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
            case "throw":
                ast = parseThrow();
                break;
            case "switch":
                ast = parseSwitch();
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

    private AST.Throw parseThrow(){
        AST.Throw ast = new AST.Throw();

        ast.calc = parseCalc();

        if(!CompilerUtil.isSuperClass(ast.calc.type, "java.lang.Throwable")) throw new RuntimeException();

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

            if(!th.hasNext() && hasNextLine()){
                int index = th.getIndex();
                nextLine();
                if(th.isNext("else")) th.last();
                else{
                    lastLine();
                    th.setIndex(index);
                }
            }
        }else{
            ast.ast = parseContent();
            if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
        }

        AST.If current = ast;
        boolean end = false;
        while (th.hasNext() && !end) {
            current = (current.elif = new AST.If());
            th.assertToken("else");

            if (th.assertToken("if", "{", "->").equals("if")) {
                current.condition = parseCalc();
                if (!current.condition.type.equals("boolean"))
                    throw new RuntimeException("Expected type boolean got " + current.condition.type);
                th.assertToken("{", "->");
            }else end = true;

            if(th.current().equals("->")){
                current.ast = new AST[]{parseNextStatement()};

                if(!th.hasNext() && hasNextLine()){
                    int index = th.getIndex();
                    nextLine();
                    if(th.isNext("else")) th.last();
                    else{
                        lastLine();
                        th.setIndex(index);
                    }
                }
            }else if(th.current().equals("{")){
                current.ast = parseContent();
                if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
            }
        }

        return ast;
    }

    private AST.Switch parseSwitch(){
        AST.Switch ast = new AST.Switch();

        ast.calc = parseCalc();
        ast.type = ast.calc.type;
        th.assertToken("{");
        th.assertNull();

        if(!(ast.type.equals("int") || ast.type.equals("short") || ast.type.equals("byte") || ast.type.equals("char") || CompilerUtil.isSuperClass(ast.type, "java.lang.Enum") || ast.type.equals("java.lang.String"))) throw new RuntimeException("illegal type "+ast.type);

        ArrayList<AST[]> branches = new ArrayList<>();
        while(hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if(th.isNext("}")) break;

                boolean defauld = false;

                if(th.assertToken("case", "default").equals("case")){
                    do{
                        Token t = th.assertToken(Token.Type.INTEGER, Token.Type.SHORT, Token.Type.CHAR, Token.Type.IDENTIFIER, Token.Type.STRING);
                        ast.values.put(t, branches.size());

                        if (!(t.t.toString().equals(ast.type) || (t.t == Token.Type.IDENTIFIER && CompilerUtil.getFieldType(ast.type, t.s, true, false) != null))) throw new RuntimeException("Expected type " + ast.type + " got " + t.t.toString());
                    }while(th.isNext(","));
                }else defauld = true;

                if(th.assertToken("->", "{").equals("->")) branches.add(new AST[]{parseNextStatement()});
                else{
                    branches.add(parseContent());
                    if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
                }

                if(defauld){
                    if(ast.defauld != null) throw new RuntimeException("default is already defined");
                    ast.defauld = branches.get(branches.size() - 1);
                    branches.remove(branches.size() - 1);
                }
            }
        }

        ast.branches = branches.toArray(new AST[0][0]);
        if(!th.current().equals("}")) throw new RuntimeException("Expected }");

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

        boolean constant = th.current().equals("const");
        if(constant) th.assertToken(Token.Type.IDENTIFIER);

        int i = 1;

        while(th.isNext("[")){
            i++;
            if(th.isNext("]")) i++;
            else break;
        }

        if(th.isNext(Token.Type.IDENTIFIER) && i % 2 != 0){
            for(int j = 0;j <= i;j++) th.last();

            String type = th.assertToken(Token.Type.IDENTIFIER).s;

            while(th.isNext("[")){
                th.assertToken("]");
                type = "["+type;
            }

            String name = th.assertToken(Token.Type.IDENTIFIER).s;
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            assertEndOfStatement();

            type = method.validateType(type, false);

            if(CompilerUtil.isPrimitive(type)){
                if(!type.equals(calc.type)) throw new RuntimeException("Expected type "+type+" got "+calc.type);
            }else if(!calc.type.equals("null")){
                if(!CompilerUtil.isSuperClass(calc.type, type)) throw new RuntimeException("Expected type "+type+" got "+calc.type);
            }
            if(scope.getType(name) != null) throw new RuntimeException("variable "+name+" is already defined");

            scope.add(name, type, constant);

            AST.VarAssignment ast = new AST.VarAssignment();

            ast.calc = calc;
            ast.type = type;
            ast.load = new AST.Load();
            ast.load.type = type;
            ast.load.name = name;
            ast.load.clazz = type;

            return ast;
        }else{
            if(constant) throw new RuntimeException("illegal argument");

            for(int j = 0;j < i;j++) th.last();
            if(th.isValid() && th.current().equals(Token.Type.IDENTIFIER)) th.last();
            AST.Load load = parseCall();

            if(load.finaly) assertEndOfStatement();

            if (th.hasNext()) {
                th.assertToken("=");
                AST.Calc calc = parseCalc();
                assertEndOfStatement();

                if(load.call == null && load.name != null && scope.isConst(load.name)) throw new RuntimeException(load.name+" is constant and can't be modified");
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

        if(th.isNext("(")){
            ast = parseCalc();
            th.assertToken(")");
        }else{
            AST.CalcArg arg = parseValue();

            if(arg instanceof AST.Value && ((AST.Value) arg).op != null && CompilerUtil.isPrimitive(arg.type)){
                if(((AST.Value) arg).op.equals("++") || ((AST.Value) arg).op.equals("--")){
                    String op = ((AST.Value) arg).op.substring(1);
                    ((AST.Value) arg).op = null;
                    ast.arg = arg;
                    ast.type = arg.type;
                    ast.setRight();
                    ast.op = "=";
                    ast.type = arg.type;
                    ast.left = new AST.Calc();
                    ast.left.type = ast.type;
                    ast.left.op = op;
                    AST.Value value = new AST.Value();
                    value.token = new Token("1", Token.Type.value(ast.type));
                    value.type = ast.type;
                    ast.left.arg = value;
                    ast.left.right = new AST.Calc();
                    ast.left.right.type = ast.type;
                    ast.left.right.arg = arg;
                }else{
                    ((AST.Value) arg).op = null;
                    ast.arg = arg;
                    ast.type = arg.type;
                    ast.setRight();
                    ast.type = "boolean";
                    ast.op = "!=";
                    AST.Value value = new AST.Value();
                    value.token = new Token("true", Token.Type.IDENTIFIER);
                    value.type = ast.type;
                    ast.arg = value;
                    ast.right = new AST.Calc();
                    ast.right.type = ast.type;
                    ast.right.arg = arg;
                }
            }else{
                ast.arg = arg;
                ast.type = arg.type;
            }
        }

        while(th.hasNext()){
            if(!th.next().equals(Token.Type.OPERATOR) || th.current().equals("->")){
                th.last();
                return ast;
            }

            ast.setRight();
            ast.op = th.current().s;

            if(ast.op.equals("=")){
                ast.left = parseCalc();
            }else if(ast.op.equals(">>") && !CompilerUtil.isPrimitive(ast.right.type) && !CompilerUtil.isPrimitive(th.assertToken(Token.Type.IDENTIFIER).s)){
                AST.Value value = new AST.Value();

                value.token = th.current();
                if(!method.uses.containsKey(value.token.s)) throw new RuntimeException("Class "+value.token.s+" is not defined");
                value.token = new Token(method.uses.get(value.token.s), null);

                ast.arg = value;
                ast.type = "boolean";
            }else{
                if(th.isNext("(")){
                    ast.left = parseCalc();
                    th.assertToken(")");
                    ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.left.type, ast.op);
                }else{
                    AST.CalcArg arg = parseValue();

                    if(arg instanceof AST.Value && ((AST.Value) arg).op != null && CompilerUtil.isPrimitive(arg.type)){
                        if(((AST.Value) arg).op.equals("++") || ((AST.Value) arg).op.equals("--")){
                            String op = ((AST.Value) arg).op.substring(1);
                            ((AST.Value) arg).op = null;
                            AST.Calc left = new AST.Calc();
                            left.arg = arg;
                            left.type = arg.type;
                            left.setRight();
                            left.op = "=";
                            left.type = arg.type;
                            left.left = new AST.Calc();
                            left.left.type = left.type;
                            left.left.op = op;
                            AST.Value value = new AST.Value();
                            value.token = new Token("1", Token.Type.value(left.type));
                            value.type = left.type;
                            left.left.arg = value;
                            left.left.right = new AST.Calc();
                            left.left.right.type = left.type;
                            left.left.right.arg = arg;

                            ast.left = left;
                            ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.left.type, ast.op);
                        }else{
                            ((AST.Value) arg).op = null;
                            AST.Calc left = new AST.Calc();
                            left.arg = arg;
                            left.type = arg.type;
                            left.setRight();
                            left.type = "boolean";
                            left.op = "!=";
                            AST.Value value = new AST.Value();
                            value.token = new Token("true", Token.Type.IDENTIFIER);
                            value.type = left.type;
                            left.arg = value;
                            left.right = new AST.Calc();
                            left.right.type = left.type;
                            left.right.arg = arg;

                            ast.left = left;
                            ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.left.type, ast.op);
                        }
                    }else{
                        ast.arg = arg;
                        ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.arg.type, ast.op);
                    }
                }
            }

            if(ast.type == null) throw new RuntimeException("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.arg.type);
        }

        return ast;
    }

    private AST.CalcArg parseValue(){
        th.next();

        if(th.current().equals(Token.Type.IDENTIFIER) && (CompilerUtil.isPrimitive(th.current().s) || method.uses.containsKey(th.current().s))){
            if(th.isNext(Token.Type.OPERATOR) || th.isNext(Token.Type.SIMPLE)){
                th.last();
            }else{
                AST.Cast ast = new AST.Cast();

                ast.cast = CompilerUtil.isPrimitive(th.current().s) ? th.current().s : method.uses.get(th.current().s);
                ast.calc = parseCalc();
                ast.type = ast.cast;

                if(!CompilerUtil.canCast(ast.calc.type, ast.cast)) throw new RuntimeException("Unable to cast "+ast.calc.type+" to "+ast.cast);

                return ast;
            }
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
            case SIMPLE:
                if(th.current().equals("{")) return parseArrayCreation();
                else throw new RuntimeException("illegal argument "+th.current());
            case OPERATOR:
                if(th.current().equals("-") && (th.isNext(Token.Type.INTEGER) || th.isNext(Token.Type.DOUBLE) || th.isNext(Token.Type.LONG) || th.isNext(Token.Type.SHORT) || th.isNext(Token.Type.FLOAT))){
                    ast.token = new Token("-"+th.current().s, th.current().t);
                    ast.type = th.current().t.toString();
                    break;
                }else{
                    String op = th.current().s;
                    AST.CalcArg arg = parseValue();

                    if(arg instanceof AST.Cast) throw new RuntimeException("Expected value");

                    ((AST.Value) arg).op = op;
                    String type = CompilerUtil.getOperatorReturnType(arg.type, op);
                    if(type == null) throw new RuntimeException("Operator "+op+" is not defined for "+arg.type);
                    arg.type = type;

                    return arg;
                }
            default:
                throw new RuntimeException("illegal argument "+th.current());
        }

        return ast;
    }

    private AST.ArrayCreation parseArrayCreation(){
        if(th.isNext("}")) throw new RuntimeException("Expected values");
        ArrayList<AST.Calc> calcs = new ArrayList<>();

        AST.Calc calc = parseCalc();
        String type = calc.type;
        calcs.add(calc);

        while(th.assertToken(",", "}").equals(",")){
            if(th.isNext("{")) throw new RuntimeException("illegal argument");
            calc = parseCalc();
            calcs.add(calc);
            if(!calc.type.equals(type)) throw new RuntimeException("Expected type "+type+" got "+calc.type);
        }

        AST.ArrayCreation ast = new AST.ArrayCreation();
        ast.type = "["+type;
        ast.calcs = calcs.toArray(new AST.Calc[0]);
        return ast;
    }

    private AST.Load parseCall(){
        AST.Load ast = new AST.Load();

        String call = th.assertToken(Token.Type.IDENTIFIER).s;

        if(CompilerUtil.isPrimitive(call) || method.uses.containsKey(call)){
            ast.call = new AST.Call();

            if(th.isNext("[")){
                th.last();

                ast.call.clazz = CompilerUtil.isPrimitive(call) ? call : method.uses.get(call);
                ast.call.call = "<init>";

                ArrayList<AST.Calc> args = new ArrayList<>();

                while(th.isNext("[")){
                    args.add(parseCalc());
                    ast.call.clazz = "["+ast.call.clazz;

                    if(!args.get(args.size() - 1).type.equals("int")) throw new RuntimeException("Expected int got "+args.get(args.size() - 1).type);

                    th.assertToken("]");
                }

                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                ast.call.type = ast.call.clazz;
                ast.type = ast.call.type;
            }else if(th.isNext("(")){
                if(CompilerUtil.isPrimitive(call)) throw new RuntimeException("illegal type "+call);

                ast.call.clazz = method.uses.get(call);
                ast.call.call = "<init>";

                StringBuilder desc = new StringBuilder("<init>");
                ArrayList<AST.Calc> args = new ArrayList<>();

                if(!th.isNext(")")){
                    while(th.hasNext()){
                        args.add(parseCalc());
                        if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                        else break;
                    }
                }

                for (AST.Calc calc:args) desc.append("%").append(calc.type);

                if (CompilerUtil.getMethodReturnType(ast.call.clazz, desc.toString(), false, ast.call.clazz.equals(clazzName)) == null)
                    throw new RuntimeException("Method "+desc+" is not defined for class "+ast.call.clazz);

                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                ast.call.type = method.uses.get(call);
                ast.type = ast.call.type;
            }else{
                if(CompilerUtil.isPrimitive(call)) throw new RuntimeException("illegal type "+call);

                th.assertToken(".");
                ast.call.clazz = method.uses.get(call);

                call = th.assertToken(Token.Type.IDENTIFIER).s;

                if(th.isNext("(")) {
                    StringBuilder desc = new StringBuilder(call);
                    ArrayList<AST.Calc> args = new ArrayList<>();

                    if (!th.isNext(")")) {
                        while (th.hasNext()) {
                            args.add(parseCalc());
                            if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                            else break;
                        }
                    }

                    for (AST.Calc calc : args) desc.append("%").append(calc.type);

                    ast.type = CompilerUtil.getMethodReturnType(ast.call.clazz, desc.toString(), true, ast.call.clazz.equals(clazzName));
                    ast.call.type = ast.type;

                    if (ast.type == null)
                        throw new RuntimeException("static Method " + desc + " is not defined for class " + ast.call.clazz);

                    ast.call.statik = true;
                    ast.call.argTypes = args.toArray(new AST.Calc[0]);
                    ast.call.call = call;
                }else{
                    ast.call.call = call;
                    ast.call.type = CompilerUtil.getFieldType(ast.call.clazz, call, true, ast.call.clazz.equals(clazzName));

                    if(ast.call.type == null) throw new RuntimeException("Static Field "+call+" is not defined for class "+ ast.call.clazz);

                    ast.call.statik = true;
                    ast.type = ast.call.type;
                }
            }
        }else{
            if(scope.getType(call) != null) {
                ast.name = call;
                ast.type = scope.getType(call);
                ast.clazz = ast.type;
            }else if(th.isNext("(")){
                ast.call = new AST.Call();
                ast.call.clazz = clazzName;
                ast.call.call = call;

                StringBuilder desc = new StringBuilder(call);
                ArrayList<AST.Calc> args = new ArrayList<>();

                if (!th.isNext(")")) {
                    while (th.hasNext()) {
                        args.add(parseCalc());
                        if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                        else break;
                    }
                }

                for (AST.Calc calc : args) desc.append("%").append(calc.type);

                ast.type = CompilerUtil.getMethodReturnType(clazzName, desc.toString(), false, true);

                if(ast.type == null){
                    ast.type = CompilerUtil.getMethodReturnType(clazzName, desc.toString(), true, true);
                    ast.call.statik = true;
                }

                ast.call.type = ast.type;

                if (ast.type == null)
                    throw new RuntimeException("static Method " + desc + " is not defined for class " + ast.call.clazz);

                ast.call.argTypes = args.toArray(new AST.Calc[0]);
            }else{
                ast.call = new AST.Call();
                ast.call.clazz = clazzName;

                ast.call.call = call;
                ast.call.type = CompilerUtil.getFieldType(clazzName, call, false, true);

                if(ast.call.type == null){
                    ast.call.type = CompilerUtil.getFieldType(clazzName, call, true, true);
                    ast.call.statik = true;
                }

                if(ast.call.type == null) throw new RuntimeException("Field "+call+" is not defined for class "+ ast.call.clazz);

                ast.type = ast.call.type;
            }

            while(th.isNext("[")){
                if(ast.call == null){
                    ast.call = new AST.Call();
                    ast.call.clazz = ast.type;
                }else {
                    if (!ast.call.type.startsWith("["))
                        throw new RuntimeException("Expected array got " + ast.call.type);

                    ast.call.setPrev();
                    ast.call.clazz = ast.call.prev.type;
                }

                ast.call.type = ast.call.clazz.substring(1);
                ast.call.argTypes = new AST.Calc[]{parseCalc()};
                ast.call.call = "";
                th.assertToken("]");

                if(!ast.call.argTypes[0].type.equals("int")) throw new RuntimeException("Expected type int got "+ast.call.argTypes[0].type);
            }

            if(ast.call != null) ast.type = ast.call.type;
        }

        if(th.isNext(".")) ast.call = parseCallArg(ast.call, ast.type);

        if(ast.call != null) ast.type = ast.call.type;

        return ast;
    }

    private AST.Call parseCallArg(AST.Call call, String currentClass){
        if(call != null) call.setPrev();
        else call = new AST.Call();

        String name = th.assertToken(Token.Type.IDENTIFIER).s;
        call.clazz = currentClass;

        if(th.isNext("(")){
            StringBuilder desc = new StringBuilder(name);
            ArrayList<AST.Calc> args = new ArrayList<>();

            if(!th.isNext(")")){
                while (th.hasNext()) {
                    args.add(parseCalc());
                    if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                    else break;
                }
            }

            for (AST.Calc calc:args) desc.append("%").append(calc.type);

            call.argTypes = args.toArray(new AST.Calc[0]);
            call.call = name;
            call.type = CompilerUtil.getMethodReturnType(call.clazz, desc.toString(), false, call.clazz.equals(clazzName));

            if(call.type == null) throw new RuntimeException("Method "+desc+" is not defined for class "+currentClass);
        }else{
            call.call = name;
            call.type = CompilerUtil.getFieldType(call.clazz, name, false, call.clazz.equals(clazzName));

            if(call.type == null) throw new RuntimeException("Field "+name+" is not defined for class "+currentClass);
        }

        while(th.isNext("[")){
            if(!call.type.startsWith("[")) throw new RuntimeException("Expected array got "+call.type);

            call.setPrev();
            call.clazz = call.prev.type;
            call.type = call.clazz.substring(1);
            call.argTypes = new AST.Calc[]{parseCalc()};
            call.call = "";
            th.assertToken("]");

            if(!call.argTypes[0].type.equals("int")) throw new RuntimeException("Expected type int got "+call.argTypes[0].type);
        }

        if(th.isNext(".")) call = parseCallArg(call, call.type);

        return call;
    }

    private void assertEndOfStatement(){
        if(!th.isNext(";")) th.assertNull();
    }

    private boolean hasNextStatement(){
        return th.hasNext() || hasNextLine();
    }

    private void nextLine(){
        method.line++;
        index++;
        th = Lexer.lex(code[index]);
    }

    private void lastLine(){
        method.line--;
        index--;
        th = Lexer.lex(code[index]);
    }

    private boolean hasNextLine(){
        return index < code.length - 1;
    }
}
