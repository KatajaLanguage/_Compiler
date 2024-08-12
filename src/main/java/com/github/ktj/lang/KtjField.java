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

import java.util.ArrayList;
import java.util.HashMap;

public class KtjField extends Compilable{

    public String type, initValue;

    public KtjField(Modifier modifier, String type, ArrayList<GenericType> genericTypes, String initValue, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, genericTypes, uses, statics, file, line);
        this.type = type;
        this.initValue = initValue;
    }

    public String correctType(){
        int i = genericIndex(type);
        if(i == -1) return type;
        assert genericTypes != null;
        return genericTypes.get(i).type;
    }

    @Override
    public void validateTypes() {
        type = validateType(type, true);
    }
}
