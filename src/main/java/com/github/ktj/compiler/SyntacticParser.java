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
                if(method.parameter[i].constant) constants.add(method.parameter[i].name);
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

    private HashMap<String, String> typeValues;
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

        setUpTypeValues();
        ArrayList<AST> ast = new ArrayList<>();

        if(this.code.length == 0) return new AST[]{CompilerUtil.getDefaultReturn(method.returnType)};

        nextLine();

        while(hasNextStatement()){
            try {
                AST e = parseNextStatement(false);
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

    private AST parseNextStatement(boolean inLoop){
        if(!hasNextStatement()) return null;

        while((!th.hasNext() || th.isEmpty()) && hasNextLine()) nextLine();

        if(th.isNext("}")){
            th.last();
            return null;
        }

        AST ast;

        switch(th.assertToken("_", Token.Type.IDENTIFIER, Token.Type.OPERATOR).s){
            case "for":
                ast = parseFor();
                break;
            case "while":
                ast = parseWhile();
                break;
            case "if":
                ast = parseIf(inLoop);
                break;
            case "break":
                if(!inLoop) throw new RuntimeException("Nothing to break");
                assertEndOfStatement();
                ast = new AST.Break();
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
            case "try":
                ast = parseTry(inLoop);
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

        if(!CompilerUtil.isSuperClass(ast.calc.type, "java.lang.Throwable")) throw new RuntimeException("Expected type java.lang.Throwable got "+ast.calc.type);

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

        scope = new Scope(scope);

        if(th.assertToken("{", "->").equals("->")){
            ast.ast = new AST[]{parseNextStatement(true)};
        }else ast.ast = parseContent(true);

        scope = scope.last;

        return ast;
    }

    private AST.If parseIf(boolean inLoop){
        AST.If ast = new AST.If();

        th.assertHasNext();
        ast.condition = parseCalc();
        if(!ast.condition.type.equals("boolean")) throw new RuntimeException("Expected type boolean got "+ast.condition.type);

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")){
            ast.ast = new AST[]{parseNextStatement(inLoop)};

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
            ast.ast = parseContent(inLoop);
            if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
        }

        scope = scope.last;

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
                scope = new Scope(scope);
                current.ast = new AST[]{parseNextStatement(inLoop)};
                scope = scope.last;

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
                scope = new Scope(scope);

                current.ast = parseContent(inLoop);
                if (!th.current().equals("}")) throw new RuntimeException("illegal argument");

                scope = scope.last;
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

                        if (!(t.t.toString().equals(ast.type) || (t.t == Token.Type.IDENTIFIER && CompilerUtil.getFieldType(ast.type, t.s, true, clazzName) != null))) throw new RuntimeException("Expected type " + ast.type + " got " + t.t.toString());
                    }while(th.isNext(","));
                }else defauld = true;

                if(th.assertToken("->", "{").equals("->")) branches.add(new AST[]{parseNextStatement(true)});
                else{
                    branches.add(parseContent(true));
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

    private AST.For parseFor(){
        AST.For ast = new AST.For();
        ast.variable = th.assertToken(Token.Type.IDENTIFIER).s;
        if(scope.getType(ast.variable) != null) throw new RuntimeException("Variable "+ast.variable+" is already defined");
        th.assertToken("in");
        if(th.isNext("{")){
            th.assertToken(Token.Type.INTEGER, Token.Type.SHORT, Token.Type.DOUBLE, Token.Type.FLOAT);
            Token.Type type = th.current().t;
            ast.from = th.current();
            th.assertToken(",");
            ast.step = th.assertToken(type);
            th.assertToken(".");
            th.assertToken(".");
            ast.to = parseCalc();
            if(!ast.to.type.equals(type.toString())) throw new RuntimeException("Expected type "+type+" got " + ast.to.type);
            ast.type = type.toString();
            th.assertToken("}");
        }else {
            ast.load = parseCall();
            if (!CompilerUtil.isSuperClass(ast.load.type, "java.lang.Iterable") && !ast.load.type.startsWith("["))
                throw new RuntimeException("Expected type java.lang.Iterable got " + ast.load.type);
            ast.type = ast.load.type;
        }

        scope.add(ast.variable, ast.type, false);

        if(th.assertToken("{", "->").equals("->")){
            ast.ast = new AST[]{parseNextStatement(true)};
        }else ast.ast = parseContent(true);
        assertEndOfStatement();

        return ast;
    }

    private AST.TryCatch parseTry(boolean inLoop){
        AST.TryCatch ast = new AST.TryCatch();

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")) ast.tryAST = new AST[]{parseNextStatement(inLoop)};
        else{
            ast.tryAST = parseContent(inLoop);
            if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
        }

        scope = scope.last;

        th.assertToken("catch");
        th.assertToken("(");
        ast.type = th.assertToken(Token.Type.IDENTIFIER).s;
        if(!method.uses.containsKey(ast.type)) throw new RuntimeException("Unknown type "+ast.type);
        ast.type = method.uses.get(ast.type);
        if(!CompilerUtil.isSuperClass(ast.type, "java.lang.Exception")) throw new RuntimeException("Expected type java.lang.Exception got "+ast.type);
        ast.variable = th.assertToken(Token.Type.IDENTIFIER).s;
        if(scope.getType(ast.variable) != null) throw new RuntimeException("Variable "+ast.variable+" is already defined");
        scope.add(ast.variable, ast.type, false);
        th.assertToken(")");

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")) ast.catchAST = new AST[]{parseNextStatement(inLoop)};
        else{
            ast.catchAST = parseContent(inLoop);
            if (!th.current().equals("}")) throw new RuntimeException("illegal argument");
        }

        scope = scope.last;

        assertEndOfStatement();
        return ast;
    }

    private AST[] parseContent(boolean inLoop){
        scope = new Scope(scope);
        ArrayList<AST> astList = new ArrayList<>();

        while(!th.isNext("}")){
            AST current = parseNextStatement(inLoop);
            if(current != null) astList.add(current);
        }

        if(!th.current().equals("}")) throw new RuntimeException("Expected }");

        scope = scope.last;
        return astList.toArray(new AST[0]);
    }

    private AST parseStatement(){
        if(!th.isNext(Token.Type.IDENTIFIER) && !th.isNext("_") && !th.isNext(Token.Type.OPERATOR)) throw new RuntimeException("illegal argument");

        boolean constant = th.current().equals("const");
        if(constant) th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR, "_");

        if(th.current().equals("_")){
            String name = th.assertToken(Token.Type.IDENTIFIER).s;
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            assertEndOfStatement();

            if(scope.getType(name) != null) throw new RuntimeException("variable "+name+" is already defined");
            scope.add(name, calc.type, constant);

            AST.VarAssignment ast = new AST.VarAssignment();
            ast.calc = calc;
            ast.type = calc.type;
            ast.load = new AST.Load();
            ast.load.type = ast.type;
            ast.load.name = name;
            ast.load.clazz = ast.type;

            return ast;
        }else{
            if(th.current().equals(Token.Type.OPERATOR)){
                if(constant) throw new RuntimeException("illegal argument");
                th.last();
                return parseAssignment();
            }else if (th.isNext(Token.Type.OPERATOR) || th.isNext(".") || th.isNext("(")){
                if(constant) throw new RuntimeException("illegal argument");
                th.last();
                th.last();
                return parseAssignment();
            }

            int i = th.getIndex();
            while(th.isNext("[")){
                if(!th.isNext("]")){
                    if(constant) throw new RuntimeException("illegal argument");
                    th.setIndex(i);
                    th.last();
                    return parseAssignment();
                }
            }

            th.setIndex(i);
            String type = th.current().s;

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
        }
    }

    private AST parseAssignment(){
        int i = th.getIndex();
        if(i > -1 && th.current().equals(Token.Type.OPERATOR)) return parseCalc();

        AST.Load load = parseCall();
        if(load.finaly) assertEndOfStatement();

        if(th.isNext("=")){
            AST.Calc calc = parseCalc();
            assertEndOfStatement();

            if(load.call == null && load.name != null && scope.isConst(load.name)) throw new RuntimeException(load.name+" is constant and can't be modified");
            if(!load.type.equals(calc.type) && !(calc.type.equals("null") && !CompilerUtil.isPrimitive(load.type))) throw new RuntimeException("Expected type "+load.type+" got "+calc.type);

            AST.VarAssignment ast = new AST.VarAssignment();
            ast.calc = calc;
            ast.load = load;
            ast.type = ast.calc.type;

            return ast;
        }else if(th.hasNext()){
            th.setIndex(i);
            return parseCalc();
        }

        return load;
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
            if((!th.next().equals(Token.Type.OPERATOR) || th.current().equals("->")) && !th.current().equals("?")){
                th.last();
                return ast;
            }

            if(th.current().equals("?")){
                AST.InlineIf arg = new AST.InlineIf();
                if(!ast.type.equals("boolean")) throw new RuntimeException("Expected type boolean got "+ast.type);
                arg.condition = ast;
                arg.trueValue = parseCalc();
                arg.type = arg.trueValue.type;
                th.assertToken(":");
                arg.falseValue = parseCalc();
                if(!arg.type.equals(arg.falseValue.type)) throw new RuntimeException("Expected type "+ast.type+" got "+arg.falseValue.type);

                ast = new AST.Calc();
                ast.arg = arg;
                ast.type = ast.arg.type;

                return ast;
            }

            ast.setRight();
            ast.op = th.current().s;

            if(ast.op.equals("=")){
                ast.left = parseCalc();
            }else if(CompilerUtil.isPrimitive(ast.right.type) && (ast.op.equals("+=") || ast.op.equals("-=") || ast.op.equals("*=") || ast.op.equals("/=") || ast.op.equals("%="))){
                String op = ast.op.substring(0, 1);
                ast.op = "=";
                AST.Calc left = new AST.Calc();
                left.arg = ast.right.arg;
                left.type = ast.type;
                left.setRight();
                left.op = op;
                left.type = ast.type;
                left.left = parseCalc();
                ast.left = left;
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
                    String[] methodSpecs = CompilerUtil.getOperatorReturnType(ast.right.type, ast.left.type, ast.op);
                    if(methodSpecs == null) throw new RuntimeException("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.left.type);
                    ast.type = methodSpecs[0];
                    ast.op = methodSpecs[1];
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
                            ast.type = arg.type;
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
                            ast.type = "boolean";
                        }
                    }else{
                        ast.arg = arg;
                        String[] methodSpecs = CompilerUtil.getOperatorReturnType(ast.right.type, ast.arg.type, ast.op);
                        if(methodSpecs == null) throw new RuntimeException("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.arg.type);
                        ast.type = methodSpecs[0];
                        ast.op = methodSpecs[1];
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
                    if(typeValues.containsKey(th.current().s)){
                        if(th.isNext("(") || th.isNext(".")) th.last();
                        else{
                            String value = th.current().s;
                            String clazz = typeValues.get(th.current().s);
                            ast.type = clazz;
                            ast.load = new AST.Load();
                            ast.load.type = clazz;
                            ast.load.call = new AST.Call();
                            ast.load.call.type = clazz;
                            ast.load.call.name = value;
                            ast.load.call.clazz = clazz;
                            ast.load.call.statik = true;
                            break;
                        }
                    }
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
                    String[] methodsSpecs = CompilerUtil.getOperatorReturnType(arg.type, op);
                    if(methodsSpecs == null) throw new RuntimeException("Operator "+op+" is not defined for "+arg.type);
                    arg.type = methodsSpecs[0];
                    ((AST.Value) arg).op = methodsSpecs[1];

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

        while(th.assertToken(",", ".", "}").equals(",")){
            //if(th.isNext("{")) throw new RuntimeException("illegal argument");
            calc = parseCalc();
            calcs.add(calc);
            if(!calc.type.equals(type)) throw new RuntimeException("Expected type "+type+" got "+calc.type);
        }

        if(th.current().equals(".")){
            th.assertToken(".");
            if(calcs.size() != 2) throw new RuntimeException("Illegal number of arguments");
            calc = parseCalc();
            calcs.add(calc);
            if(!calc.type.equals(type)) throw new RuntimeException("Expected type "+type+" got "+calc.type);
            for(AST.Calc c:calcs) if(!c.isSingleValue()) throw new RuntimeException("illegal argument");
            switch(type){
                case "int":
                case "short":
                    int current = Integer.parseInt(((AST.Value)(calcs.get(0).arg)).token.s);
                    int step = Integer.parseInt(((AST.Value)(calcs.get(1).arg)).token.s) - current;
                    int end = Integer.parseInt(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increase = current < end;
                    if(step == 0 || increase != (step > 0)) throw new RuntimeException("illegal argument");
                    calcs = new ArrayList<>();
                    while(increase ? current <= end : current >= end){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(current), Token.Type.value(type));
                        calcs.add(calc);
                        current += step;
                    }
                    break;
                case "long":
                    long currentL = Long.parseLong(((AST.Value)(calcs.get(0).arg)).token.s);
                    long stepL = Long.parseLong(((AST.Value)(calcs.get(1).arg)).token.s) - currentL;
                    long endL = Long.parseLong(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseL = currentL < endL;
                    if(stepL == 0 || increaseL != (stepL > 0)) throw new RuntimeException("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseL ? currentL <= endL : currentL >= endL){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentL), Token.Type.LONG);
                        calcs.add(calc);
                        currentL += stepL;
                    }
                    break;
                case "double":
                    double currentD = Double.parseDouble(((AST.Value)(calcs.get(0).arg)).token.s);
                    double stepD = Double.parseDouble(((AST.Value)(calcs.get(1).arg)).token.s) - currentD;
                    double endD = Double.parseDouble(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseD = currentD < endD;
                    if(stepD == 0 || increaseD != (stepD > 0)) throw new RuntimeException("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseD ? currentD <= endD : currentD >= endD){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentD), Token.Type.DOUBLE);
                        calcs.add(calc);
                        currentD += stepD;
                    }
                    break;
                case "float":
                    float currentF = Float.parseFloat(((AST.Value)(calcs.get(0).arg)).token.s);
                    float stepF = Float.parseFloat(((AST.Value)(calcs.get(1).arg)).token.s) - currentF;
                    float endF = Float.parseFloat(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseF = currentF < endF;
                    if(stepF == 0 || increaseF != (stepF > 0)) throw new RuntimeException("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseF ? currentF <= endF : currentF >= endF){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentF), Token.Type.FLOAT);
                        calcs.add(calc);
                        currentF += stepF;
                    }
                    break;
                default:
                    throw new RuntimeException("expected number got "+type);
            }

            th.assertToken("}");
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
                ast.call.name = "<init>";

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
                ast.call.name = "<init>";

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

                String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, false, desc.toString(), clazzName);
                if(methodSpecs == null)
                    throw new RuntimeException("Method "+desc+" is not defined for class "+ast.call.clazz);

                ast.call.type = methodSpecs[0];
                ast.call.signature = methodSpecs[1];
                ast.call.argTypes = args.toArray(new AST.Calc[0]);
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

                    String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    if(methodSpecs == null)
                        throw new RuntimeException("Static Method "+desc+" is not defined for class "+ast.call.clazz);

                    ast.call.name = call;
                    ast.call.type = methodSpecs[0];
                    ast.call.signature = methodSpecs[1];
                    ast.type = ast.call.type;
                    ast.call.statik = true;
                    ast.call.argTypes = args.toArray(new AST.Calc[0]);
                }else{
                    ast.call.name = call;
                    ast.call.type = CompilerUtil.getFieldType(ast.call.clazz, call, true, clazzName);

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
                ast.call.name = call;

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

                String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, false, desc.toString(), clazzName);

                if(methodSpecs == null){
                    methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    ast.call.statik = true;
                }

                if(methodSpecs == null){
                    ast.call.clazz = getClazzFromMethod(desc.toString());
                    if(ast.call.clazz == null)
                        throw new RuntimeException("Method " + desc + " is not defined for class " + clazzName);
                    methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    ast.call.statik = true;
                }

                if(methodSpecs == null) throw new RuntimeException("Method "+desc+" is not defined for Class "+ast.call.clazz);

                ast.call.type = methodSpecs[0];
                ast.call.signature = methodSpecs[1];
                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                ast.type = ast.call.type;
            }else{
                ast.call = new AST.Call();
                ast.call.clazz = clazzName;

                ast.call.name = call;
                ast.call.type = CompilerUtil.getFieldType(clazzName, call, false, clazzName);

                if(ast.call.type == null){
                    ast.call.type = CompilerUtil.getFieldType(clazzName, call, true, clazzName);
                    ast.call.statik = true;
                }

                if(ast.call.type == null){
                    ast.call.clazz = getClazzFromField(call);
                    if(ast.call.clazz == null)
                        throw new RuntimeException("Field " + call + " is not defined for class " + clazzName);
                    ast.call.type = CompilerUtil.getFieldType(ast.call.clazz, call, true, clazzName);
                    ast.call.statik = true;
                }

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
                ast.call.name = "";
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

            String[] methodSpecs = CompilerUtil.getMethod(call.clazz, false, desc.toString(), clazzName);
            if(methodSpecs == null)
                throw new RuntimeException("Method "+desc+" is not defined for class "+currentClass);

            call.name = name;
            call.type = methodSpecs[0];
            call.signature = methodSpecs[1];
        }else{
            call.name = name;
            call.type = CompilerUtil.getFieldType(call.clazz, name, false, clazzName);

            if(call.type == null) throw new RuntimeException("Field "+name+" is not defined for class "+currentClass);
        }

        while(th.isNext("[")){
            if(!call.type.startsWith("[")) throw new RuntimeException("Expected array got "+call.type);

            call.setPrev();
            call.clazz = call.prev.type;
            call.type = call.clazz.substring(1);
            call.argTypes = new AST.Calc[]{parseCalc()};
            call.name = "";
            th.assertToken("]");

            if(!call.argTypes[0].type.equals("int")) throw new RuntimeException("Expected type int got "+call.argTypes[0].type);
        }

        if(th.isNext(".")) call = parseCallArg(call, call.type);

        return call;
    }

    private String getClazzFromMethod(String method){
        for(String name:this.method.statics){
            if(CompilerUtil.getMethod(this.method.uses.get(name), true, method, clazzName) != null) return this.method.uses.get(name);
        }
        return null;
    }

    private String getClazzFromField(String field){
        for(String name:method.statics){
            String type = CompilerUtil.getFieldType(method.uses.get(name), field, true, clazzName);
            if(type != null) return method.uses.get(name);
        }
        return null;
    }

    private void setUpTypeValues(){
        typeValues = new HashMap<>();

        for(String clazz:method.uses.values()) if(CompilerUtil.isType(clazz)) for(String type:CompilerUtil.getTypes(clazz)) if(!typeValues.containsKey(type)) typeValues.put(type, clazz);
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
