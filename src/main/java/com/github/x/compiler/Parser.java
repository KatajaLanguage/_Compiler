package com.github.x.compiler;

import com.github.x.bytecode.AccessFlag;
import com.github.x.lang.Compilable;
import com.github.x.lang.XClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

final class Parser {

    private HashMap<String, Compilable> classes;
    private Scanner sc;
    private TokenHandler th;
    private String path;
    private String name;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException(STR."File \{file.getPath()} did not exist");

        try{
            path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            name = file.getName().substring(0, file.getName().length() - 2);
            classes = new HashMap<>();
            sc = new Scanner(file);
            line = 0;
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException(STR."Unable to find \{file.getPath()}");
        }

        while(sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                switch (th.next().s()){
                    case "#" -> {}
                    case "public", "private", "protected" -> parseModifier();
                    case "class" -> parseClass(AccessFlag.ACC_PACKAGE_PRIVATE);
                    default -> err("illegal argument");
                }
            }
        }

        if(classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0])){
            String name = classes.keySet().toArray(new String[0])[0];
            Compilable clazz = classes.remove(name);
            classes.put(Compiler.validateClassName(STR."\{path}.\{name}"), clazz);
        }else{
            HashMap<String, Compilable> help = new HashMap<>();

            for(String clazz: classes.keySet()) help.put(Compiler.validateClassName(STR."\{path}.\{name}.\{clazz}"), classes.get(clazz));

            classes = help;
        }

        return classes;
    }

    private void parseModifier(){
        AccessFlag accessFlag = switch (th.current().s()){
            case "public" -> AccessFlag.ACC_PUBLIC;
            case "private" -> AccessFlag.ACC_PRIVATE;
            case "protected" -> AccessFlag.ACC_PROTECTED;
            default -> AccessFlag.ACC_PACKAGE_PRIVATE;
        };

        th.assertToken("class");
        parseClass(accessFlag);
    }

    private void parseClass(AccessFlag accessFlag){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Class \{name} is already defined");

        th.assertToken("{");
        th.assertNull();

        XClass clazz = new XClass(accessFlag, false, false);

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                switch (th.next().s()){
                    case "}" -> {
                        th.assertNull();
                        classes.put(name, clazz);
                        return;
                    }
                }
            }
        }

        err("Expected '}'");
    }

    private void nextLine() throws RuntimeException{
        if(sc.hasNextLine()){
            th = Lexer.lex(sc.nextLine());
            line ++;
        }else throw new RuntimeException();
    }

    private void err(String message) throws RuntimeException{
        throw new RuntimeException(STR."\{message} at \{path}\\\{name}:\{line}");
    }
}
