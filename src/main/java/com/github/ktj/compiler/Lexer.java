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

package com.github.ktj.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

final class Lexer{

    static TokenHandler lex(File file) throws FileNotFoundException{
        List<Token[]> result = new ArrayList<>();
        List<Token> token = new ArrayList<>();

        Scanner sc = new Scanner(file);
        List<Character> chars = new ArrayList<>();
        StringBuilder value;
        int line = 0;

        while(sc.hasNextLine()){
            for(char c:sc.nextLine().trim().toCharArray()) chars.add(c);
            chars.add('\n');
        }

        for(int i = 0; i < chars.size();i++){
            if(chars.get(i) == '\n'){
                result.add(token.toArray(new Token[0]));
                token = new ArrayList<>();
                line++;
            }else if(chars.get(i) == '#'){
                i++;
                if(chars.get(i) != '#') while (i < chars.size() && chars.get(i) != '#' && chars.get(i) != '\n') i++;
                else{
                    while(i < chars.size()){
                        i++;
                        if(chars.get(i) == '#' && chars.get(i - 1) == '#') break;
                    }
                }
            }else if(Character.isDigit(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                Token.Type type;
                while(i + 1 < chars.size() && Character.isDigit(chars.get(i + 1))){
                    i++;
                    value.append(chars.get(i));
                }
                if(i + 1 < chars.size() && chars.get(i + 1) == '.'){
                    i++;
                    value.append(chars.get(i));
                    while(i + 1 < chars.size() && Character.isDigit(chars.get(i + 1))){
                        i++;
                        value.append(chars.get(i));
                    }
                    type = Token.Type.DOUBLE;
                }else type = Token.Type.INTEGER;

                if(i + 1 < chars.size() && chars.get(i + 1) == 'f') {
                    type = Token.Type.FLOAT;
                    i++;
                    value.append("f");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'd'){
                    type = Token.Type.DOUBLE;
                    i++;
                    value.append("d");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'i'){
                    type = Token.Type.INTEGER;
                    i++;
                    value.append("i");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 's'){
                    type = Token.Type.SHORT;
                    i++;
                    value.append("s");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'l'){
                    type = Token.Type.LONG;
                    i++;
                    value.append("i");
                }

                token.add(new Token(value.toString(), type));
            }else if(Character.isLetter(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                while(i + 1 < chars.size() && (Character.isDigit(chars.get(i + 1)) || Character.isLetter(chars.get(i + 1)) || chars.get(i + 1) == '_')){
                    i++;
                    value.append(chars.get(i));
                }
                token.add(new Token(value.toString(), Token.Type.IDENTIFIER));
            }else if(isOperator(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                while(i + 1 < chars.size() && isOperator(chars.get(i + 1))){
                    i++;
                    value.append(chars.get(i));
                }
                token.add(new Token(value.toString(), Token.Type.OPERATOR));
            }else if(chars.get(i) == '"'){
                value = new StringBuilder();
                value.append("\"");
                while(chars.get(i + 1) != '"'){
                    i++;
                    value.append(chars.get(i));
                }
                value.append("\"");
                i++;
                if(chars.get(i) != '"') throw new LexingException("Expected \"", file.getPath()+"/"+file.getName(), line);
                token.add(new Token(value.toString(), Token.Type.STRING));
            }else if(chars.get(i) == '\''){
                if(chars.size() >= i + 2 && chars.get(i + 2) == '\''){
                    token.add(new Token("'"+chars.get(i + 1)+"'", Token.Type.CHAR));
                    i += 3;
                }else throw new LexingException("Expected '", file.getPath()+"/"+file.getName(), line);
            }else if(!(chars.get(i) == '\r' || chars.get(i) == '\t' || chars.get(i) == ' ')) token.add(new Token(String.valueOf(chars.get(i)), Token.Type.SIMPLE));
        }

        return new TokenHandler(result.toArray(new Token[0][0]), file.getPath()+"\\"+file.getName(), 0);
    }

    static TokenHandler lex(String code, String clazzName, int lineOffset){
        List<Token[]> result = new ArrayList<>();
        List<Token> token = new ArrayList<>();

        char[] chars = code.toCharArray();
        StringBuilder value;
        int line = 0;

        for(int i = 0; i < chars.length;i++){
            if(chars[i] == '\n'){
                result.add(token.toArray(new Token[0]));
                token = new ArrayList<>();
                line++;
            }else if(chars[i] == '#'){
                i++;
                while (i < chars.length && chars[i] != '#' && chars[i] != '\n') i++;
            }else if(Character.isDigit(chars[i])){
                value = new StringBuilder();
                value.append(chars[i]);
                Token.Type type;
                while(i + 1 < chars.length && Character.isDigit(chars[i + 1])){
                    i++;
                    value.append(chars[i]);
                }
                if(i + 1 < chars.length && chars[i + 1] == '.'){
                    i++;
                    value.append(chars[i]);
                    while(i + 1 < chars.length && Character.isDigit(chars[i + 1])){
                        i++;
                        value.append(chars[i]);
                    }
                    type = Token.Type.DOUBLE;
                }else type = Token.Type.INTEGER;

                if(i + 1 < chars.length && chars[i + 1] == 'f') {
                    type = Token.Type.FLOAT;
                    i++;
                    value.append("f");
                }else if(i + 1 < chars.length && chars[i + 1] == 'd'){
                    type = Token.Type.DOUBLE;
                    i++;
                    value.append("d");
                }else if(i + 1 < chars.length && chars[i + 1] == 'i'){
                    type = Token.Type.INTEGER;
                    i++;
                    value.append("i");
                }else if(i + 1 < chars.length && chars[i + 1] == 's'){
                    type = Token.Type.SHORT;
                    i++;
                    value.append("s");
                }else if(i + 1 < chars.length && chars[i + 1] == 'l'){
                    type = Token.Type.LONG;
                    i++;
                    value.append("i");
                }

                token.add(new Token(value.toString(), type));
            }else if(Character.isLetter(chars[i])){
                value = new StringBuilder();
                value.append(chars[i]);
                while(i + 1 < chars.length && (Character.isDigit(chars[i + 1]) || Character.isLetter(chars[i + 1]) || chars[i + 1] == '_')){
                    i++;
                    value.append(chars[i]);
                }
                token.add(new Token(value.toString(), Token.Type.IDENTIFIER));
            }else if(isOperator(chars[i])){
                value = new StringBuilder();
                value.append(chars[i]);
                while(i + 1 < chars.length && isOperator(chars[i + 1])){
                    i++;
                    value.append(chars[i]);
                }
                token.add(new Token(value.toString(), Token.Type.OPERATOR));
            }else if(chars[i] == '"'){
                value = new StringBuilder();
                value.append("\"");
                while(chars[i + 1] != '"'){
                    i++;
                    value.append(chars[i]);
                }
                value.append("\"");
                i++;
                if(chars[i] != '"') throw new LexingException("Expected \"", clazzName, line);
                token.add(new Token(value.toString(), Token.Type.STRING));
            }else if(chars[i] == '\''){
                if(chars.length >= i + 2 && chars[i + 2] == '\''){
                    token.add(new Token("'"+chars[i + 1]+"'", Token.Type.CHAR));
                    i += 3;
                }else throw new LexingException("Expected '", clazzName, line);
            }else if(!(chars[i] == '\r' || chars[i] == '\t' || chars[i] == ' ')) token.add(new Token(String.valueOf(chars[i]), Token.Type.SIMPLE));
        }

        if(!token.isEmpty()) result.add(token.toArray(new Token[0]));

        return new TokenHandler(result.toArray(new Token[0][0]), clazzName, lineOffset);
    }

    public static boolean isOperator(char c){
        return String.valueOf(c).matches("[-+*/!=<>%&|^~]");
    }
}
