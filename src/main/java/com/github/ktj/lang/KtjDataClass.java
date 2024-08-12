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

package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class KtjDataClass extends Compilable{

    public final LinkedHashMap<String, KtjField> fields;

    public KtjDataClass(Modifier modifier, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, uses, statics, file, line);
        fields = new LinkedHashMap<>();
    }

    @Override
    public void validateTypes() {
        for(KtjField field:fields.values()) field.validateTypes();
    }

    public boolean addField(boolean constant, String type, String name, int line){
        if(fields.containsKey(name)) return true;

        Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
        mod.constant = constant;

        fields.put(name, new KtjField(mod, type, null, null, uses, statics, file, line));
        return false;
    }
}
