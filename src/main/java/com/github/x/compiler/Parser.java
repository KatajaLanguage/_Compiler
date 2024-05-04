package com.github.x.compiler;

import com.github.x.lang.Compilable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

final class Parser {

    private HashMap<String, Compilable> result;
    private Scanner sc;
    private TokenHandler th;
    private File file;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException(STR."File \{file.getPath()} did not exist");

        try{
            this.file = file;
            result = new HashMap<>();
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
                    default -> err("illegal argument");
                }
            }
        }

        return result;
    }

    private void nextLine() throws RuntimeException{
        if(sc.hasNextLine()){
            th = Lexer.lex(sc.nextLine());
            line ++;
        }else throw new RuntimeException();
    }

    private void err(String message) throws RuntimeException{
        throw new RuntimeException(STR."\{message} at \{file.getPath()}:\{line}");
    }
}
