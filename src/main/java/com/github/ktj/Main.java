/*
 * Copyright (C) 2024 Xaver Weste
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
        if(execute(args)) return;

        Scanner sc = new Scanner(System.in);
        while(!execute(sc.nextLine().split(" ")));
    }

    private static boolean execute(String[] args){
        if(args.length == 0) return false;

        int i = 0;
        boolean quit = false;
        Compiler c = Compiler.NewInstance();

        if(args[i].equals("-q")) {
            quit = true;
            i++;
        }
        if(args.length <= i) return quit;

        if(args[i].equals("-d")) {
            c.setDebug(true);
            i++;
        }
        if(args.length <= i) return quit;

        if(args[i].equals("-o")){
            if(args.length == i - 1){
                System.out.println("Expected argument");
                return quit;
            }
            try {
                c.setOutFolder(args[i + 1]);
                Decompiler.setOutFolder(args[i + 1]);
            }catch(Exception e){
                e.printStackTrace();
            }
            i += 2;
        }
        if(args.length <= i) return quit;

        ArrayList<String> compile = new ArrayList<>();

        if(args[i].equals("-dc")){
            i++;

            if(args.length == i){
                System.out.println("Expected argument");
                return quit;
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
        if(args.length <= i) return quit;

        if(compile.isEmpty()){
            if (args[i].equals("-c")) {
                i++;

                if (args.length == i) {
                    System.out.println("Expected argument");
                    return quit;
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
                        return quit;
                    }
                    execute = args[i + 1];
                    i += 2;
                }

                if (args.length > i) {
                    if (args[i].equals("-i")) {
                        if (args.length == i - 1) {
                            System.out.println("Expected argument");
                            return quit;
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
        return quit;
    }
}
