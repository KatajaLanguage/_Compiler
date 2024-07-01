package com.github.ktj.compiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

public final class Main {

    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        boolean quit = false;

        while (!quit){
            Compiler c = Compiler.NewInstance();

            System.out.print("> ");
            while(!sc.hasNextLine());
            args = sc.nextLine().split(" ");

            loop:
                if(args.length > 0){
                    int i = 0;

                    if(args[i].equals("-q")){
                        quit = true;
                        i++;
                    }

                    if(args.length <= i) break loop;

                    if(args[i].equals("-d")){
                        c.setDebug(true);
                        i++;
                    }

                    if(args.length <= i) break loop;

                    if(args[i].equals("-o")){
                        if(args.length == i - 1){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        c.setOutFolder(args[i + 1]);
                        i += 2;
                    }

                    if(args.length <= i) break loop;

                    ArrayList<String> compile = new ArrayList<>();

                    if(args[i].equals("-c")){
                        i++;

                        if(args.length == i){
                            System.out.println("Expected argument");
                            break loop;
                        }

                        while(args.length > i){
                            if(args[i].startsWith("-")) break;
                            else compile.add(args[i]);
                            i++;
                        }
                    }

                    String execute = null;

                    if(args[i].equals("-e")){
                        if(args.length == i - 1){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        execute = args[i + 1];
                        i += 2;
                    }

                    InputStream shell = null;

                    if(args[i].equals("-i")){
                        if(args.length == i - 1){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        try{
                            File f = new File(args[i + 1]);
                            System.out.println(f.exists());

                            shell = System.in;
                            System.setIn(new FileInputStream(args[i + 1]));
                        }catch(FileNotFoundException e){
                            System.err.println("Unable to find "+args[i + 1]);
                        }

                        i += 2;
                    }

                    if(args.length > i){
                        System.out.println("illegal argument(s)");
                    }else{
                        for (int j = 0; j < compile.size(); j++) {
                            try {
                                c.compile(compile.get(j), false, j == 0);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (execute != null) {
                            try {
                                c.compile(execute, true, compile.isEmpty());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if(shell != null) System.setIn(shell);
                }
        }
    }
}
