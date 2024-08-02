package com.github.ktj;

import com.github.ktj.compiler.Compiler;
import com.github.ktj.decompiler.Decompiler;

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

        Compiler c = Compiler.NewInstance();

        while (!quit){
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
                        try {
                            c.setOutFolder(args[i + 1]);
                            Decompiler.setOutFolder(args[i + 1]);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        i += 2;
                    }

                    if(args.length <= i) break loop;

                    ArrayList<String> compile = new ArrayList<>();

                    if(args[i].equals("-dc")){
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

                        if(args.length == i + 1) System.out.println("illegal argument(s)");
                        else{
                            Decompiler.decompile(compile.toArray(new String[0]));
                            System.out.println("\nprocess finished successfully");
                        }
                    }

                    if(compile.isEmpty()){
                        if (args[i].equals("-c")) {
                            i++;

                            if (args.length == i) {
                                System.out.println("Expected argument");
                                break loop;
                            }

                            while (args.length > i) {
                                if (args[i].startsWith("-")) break;
                                else compile.add(args[i]);
                                i++;
                            }
                        }

                        String execute = null;
                        InputStream shell = null;

                        if (args.length > i) {
                            if (args[i].equals("-e")) {
                                if (args.length == i - 1) {
                                    System.out.println("Expected argument");
                                    break loop;
                                }
                                execute = args[i + 1];
                                i += 2;
                            }

                            if (args.length > i) {
                                if (args[i].equals("-i")) {
                                    if (args.length == i - 1) {
                                        System.out.println("Expected argument");
                                        break loop;
                                    }
                                    try {
                                        File f = new File(args[i + 1]);
                                        System.out.println(f.exists());

                                        shell = System.in;
                                        System.setIn(new FileInputStream(args[i + 1]));
                                    } catch (FileNotFoundException e) {
                                        System.err.println("Unable to find " + args[i + 1]);
                                    }

                                    i += 2;
                                }
                            }
                        }

                        if (args.length > i) {
                            System.err.println("illegal argument(s)");
                        } else {
                            try {
                                c.compile(true, compile.toArray(new String[0]));
                                if (execute != null) c.execute(execute);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            System.out.println("\nprocess finished successfully");
                        }

                        if (shell != null) System.setIn(shell);
                    }
                }
        }
    }
}
