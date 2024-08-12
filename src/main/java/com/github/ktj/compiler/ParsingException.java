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
import java.util.ArrayList;
import java.util.Collections;

public class ParsingException extends RuntimeException{

    private String file;
    private int line;

    public ParsingException(String message, String file, int line){
        super(message);
        this.file = file.replace(".", "\\");
        this.line = line;
    }

    @Override
    public StackTraceElement[] getStackTrace(){
        String className = file;
        if(file.contains("\\") && !new File(file+".ktj").exists()) file = file.substring(0, file.lastIndexOf("\\"));
        StackTraceElement element = new StackTraceElement(className, "", file.substring(file.lastIndexOf("\\") + 1)+".ktj", line);

        if(!Compiler.Instance().debug) return new StackTraceElement[]{element};
        else{
            ArrayList<StackTraceElement> stackTrace = new ArrayList<>();
            stackTrace.add(element);
            Collections.addAll(stackTrace, super.getStackTrace());
            return stackTrace.toArray(new StackTraceElement[0]);
        }
    }

    @Override
    public void printStackTrace() {
        setStackTrace(getStackTrace());
        super.printStackTrace();
    }
}
