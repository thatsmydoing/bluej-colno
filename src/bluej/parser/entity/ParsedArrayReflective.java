/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010  Michael Kolling and John Rosenberg 

 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 

 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 

 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 

 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser.entity;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import bluej.debugger.gentype.FieldReflective;
import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.GenTypeDeclTpar;
import bluej.debugger.gentype.JavaPrimitiveType;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.MethodReflective;
import bluej.debugger.gentype.Reflective;
import bluej.utility.JavaReflective;

public class ParsedArrayReflective extends Reflective
{
    private Reflective component;
    private String className;
    
    public ParsedArrayReflective(Reflective component, String componentName)
    {
        this.component = component;
        className = "[" + componentName;
    }
    
    @Override
    public String getName()
    {
        return "[L" + component.getName() + ";";
    }
    
    public String getSimpleName()
    {
        return component.getName() + "[]";
    }
    
    @Override
    public Reflective getArrayOf()
    {
        return new ParsedArrayReflective(this, className);
    }
    
    // See JLS section 10.7: arrays have a "public final int length" field
    @Override
    public Map<String,FieldReflective> getDeclaredFields()
    {
        return Collections.singletonMap("length", new FieldReflective("length", JavaPrimitiveType.getInt(), Modifier.PUBLIC | Modifier.FINAL)); 
    }
    
    // See JLS section 10.7: arrays have a "public Object clone()" method
    @Override
    public Map<String, Set<MethodReflective>> getDeclaredMethods()
    {
        return Collections.singletonMap("clone", Collections.singleton(new MethodReflective("clone", new GenTypeClass(new JavaReflective(Object.class)), new ArrayList<GenTypeDeclTpar>(), new ArrayList<JavaType>(), this, false, Modifier.PUBLIC)));
    }
    
    @Override
    public List<Reflective> getInners()
    {
        return Collections.emptyList();
    }
    
    @Override
    public Reflective getRelativeClass(String name)
    {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public List<GenTypeClass> getSuperTypes()
    {
        List<GenTypeClass> componentSupers = component.getSuperTypes();
        for (ListIterator<GenTypeClass> i = componentSupers.listIterator(); i.hasNext(); ) {
            i.set(i.next().getArray());
        }
        componentSupers.add(new GenTypeClass(new JavaReflective(Object.class)));
        return componentSupers;
    }
    
    @Override
    public List<Reflective> getSuperTypesR()
    {
        Reflective obj = new JavaReflective(Object.class);
        return Collections.singletonList(obj);
    }
    
    @Override
    public List<GenTypeDeclTpar> getTypeParams()
    {
        return Collections.emptyList();
    }
    
    @Override
    public boolean isAssignableFrom(Reflective r)
    {
        // TODO Auto-generated method stub
        return false;
    }
    
    @Override
    public boolean isInterface()
    {
        return false;
    }
    
    @Override
    public boolean isPublic()
    {
        return component.isPublic();
    }
    
    @Override
    public boolean isStatic()
    {
        return component.isStatic();
    }
}
