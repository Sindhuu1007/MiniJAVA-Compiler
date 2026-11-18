import visitor.GJDepthFirst;
import java.util.*;

public class SymbolTable{
    public boolean hasError = false;
    public Map<String,ClassInfo> classes = new HashMap<>();
    public String currentClass = null;
    public String currentMethod = null;
    public boolean debug = true;
    public void addClass(String name,String parentName){    
        if(classes.containsKey(name)){
            System.out.println("Type error");
            if(debug){
                System.err.println("addClass_ST");
            }
            System.exit(0);
        }
        classes.put(name,new ClassInfo(name,parentName));
    }
    public void addField(String className,String var,String type){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            if(toAdd.fields.containsKey(var)){
                System.out.println("Type error");
                if(debug){
                    System.err.println("addField_ST");
                }
                System.exit(0);
            }
            toAdd.addField(var,type);
        }
    }
    public void addMethod(String className,String method, String returnType){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            if(toAdd.methods.containsKey(method)){
                System.out.println("Type error");
                if(debug){
                    System.err.println("addMethod_ST");
                }
                System.exit(0);
            }
            toAdd.addMethod(method,returnType);
        }
    }
    public void addParameter(String classname,String method,String parameter,String type){
        ClassInfo toAdd = classes.get(classname);
        if(toAdd != null){
            MethodInfo minfo = toAdd.methods.get(method);
            if(minfo != null){
                if(minfo.parameters.containsKey(parameter)){
                    if(debug){
                        System.err.println("addParameter_ST");
                    }
                    System.out.println("Type error\n");
                    System.exit(0);
                }
                minfo.addParam(parameter,type);
            }
        }
    }
    public void addLocalVar(String className,String method,String var,String type){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            MethodInfo minfo = toAdd.methods.get(method);
            if(minfo != null){
                if(minfo.localVars.containsKey(var) || minfo.parameters.containsKey(var)){
                    System.out.println("Type error\n");
                    if(debug){
                        System.err.println("addLocalVar_ST");
                    } 
                    System.exit(0);
                }
                minfo.addLocalVar(var,type);
            }
        }
    }
    public void removeLocalVar(String className, String method, String var){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            MethodInfo minfo = toAdd.methods.get(method);
            if(minfo != null){
                minfo.removeLocalVar(var);
            }
        }
    }
    public void addLambda(String className,String methodName,String varName,String paramName,String paramType,String retType,String body){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            MethodInfo minfo = toAdd.methods.get(methodName);
            if(minfo != null){
                LambdaInfo linfo = new LambdaInfo(varName,paramName,paramType,retType,body);
                minfo.addLambda(linfo);
            }
            else{
                //class lo add cheyy
                LambdaInfo linfo = new LambdaInfo(varName,paramName,paramType,retType,body);
                toAdd.lambdas.add(linfo);
            }
        }
        return;
    }
    public void updateLambda(String className, String methodName, String varName, String paramName, String body){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            MethodInfo minfo = toAdd.methods.get(methodName);
            if(minfo != null){
                for(LambdaInfo linfo : minfo.lambdas){
                    if(linfo.lambdaVarName.equals(varName)){
                        linfo.paramName = paramName;
                        linfo.body = body;
                        return;
                    }
                }
            }
            else{
                for(LambdaInfo linfo : toAdd.lambdas){
                    if(linfo.lambdaVarName.equals(varName)){
                        linfo.paramName = paramName;
                        linfo.body = body;
                        return;
                    }
                }
            }
        }
        return;
    }
    public String getFieldType(String className,String fieldName){
        ClassInfo toCheck = classes.get(className);
        if(toCheck != null){
            if(toCheck.fields.containsKey(fieldName)==true){
                return toCheck.fields.get(fieldName);
            } else if(toCheck.parentName != null){
                return getFieldType(toCheck.parentName,fieldName);
            }
        }
        return null;
    }
    public String getVariableType(String className,String methodName,String varName){
        ClassInfo toCheck = classes.get(className);
        if(toCheck != null){
            if(methodName != null){
                MethodInfo minfo = toCheck.methods.get(methodName);
                if(minfo != null){
                    if(minfo.localVars.containsKey(varName)){
                        return minfo.localVars.get(varName);
                    }
                    else if(minfo.parameters.containsKey(varName)){
                        return minfo.parameters.get(varName);
                    }
                    for(LambdaInfo linfo : minfo.lambdas){
                        if(linfo.paramName.equals(varName)){
                            return linfo.paramType;
                        }
                    }
                }
            }
            // Fallback to class fields if not in method scope or not found in method
            if(toCheck.fields.containsKey(varName)){
                return toCheck.fields.get(varName);
            }
            else if(toCheck.parentName != null){
                return getVariableType(toCheck.parentName, null, varName); // Search in parent, but not in a specific method
            }
        }
        return null;
    }

    public String getMethodReturnType(String className,String methodName){
        ClassInfo toCheck = classes.get(className);
        if(toCheck!=null){
            MethodInfo minfo = toCheck.methods.get(methodName);
            if(minfo!=null){
                return minfo.returnType;
            }
            else if(toCheck.parentName!=null){
                return getMethodReturnType(toCheck.parentName,methodName);
            }
        }
        return null;
    }

    public MethodInfo getMethod(String className,String methodName){
        ClassInfo toCheck = classes.get(className);
        if(toCheck!=null){
            //System.out.println("currentClass="+className);
            MethodInfo minfo = toCheck.methods.get(methodName);
            if(minfo!=null){
                //System.out.println("currentMethod="+methodName);
                return minfo;
            }
            else if(toCheck.parentName!=null){
                //System.out.println("current="+toCheck.parentName);
                return getMethod(toCheck.parentName,methodName);
            }
        }
        return null;
    }

    public boolean possibleTypes(String expected, String actual){
        if(expected == null || actual == null){
            return false;
        }
        if(expected.equals(actual)){
            return true;
        }
        if(expected.startsWith("Function<") && actual.startsWith("Function<")){
            int comma1 = expected.indexOf(',');
            int comma2 = actual.indexOf(',');
            String param1 = expected.substring(9,comma1);
            String ret1 = expected.substring(comma1+1,expected.length()-1);
            String param2 = actual.substring(9,comma2);
            String ret2 = actual.substring(comma2+1,actual.length()-1);
            if(param1.equals("?") || param2.equals("?")){
                return possibleTypes(ret1,ret2);
            }
            // Parameters are contravariant, return types are covariant
            return possibleTypes(param2,param1) && possibleTypes(ret1,ret2);
        }
        if(classes.containsKey(actual) && classes.get(expected)!=null){ 
           return areCompatibleClasses(expected,actual);
        }
        return false;
     }

    public boolean areCompatibleClasses(String expected, String actual) {
        if (expected.equals(actual)) return true;
        ClassInfo classInfo = classes.get(actual);
        while (classInfo != null) {
            if (classInfo.className.equals(expected)){
                return true;
            }
            classInfo = classes.get(classInfo.parentName);
        }
        return false;
    }
    
    public void print() {
        System.out.println("Classes in Symbol Table:");
        for (String className : classes.keySet()) {
            ClassInfo ci = classes.get(className);

            System.out.println("Class: " + className);
            System.out.println("  Parent: " + (ci.parentName == null ? "null" : ci.parentName));

            // Fields
            System.out.print("  Fields: { ");
            boolean firstField = true;
            for (Map.Entry<String, String> f : ci.fields.entrySet()) {
                if (!firstField) System.out.print(", ");
                System.out.print(f.getKey() + "=" + f.getValue());
                firstField = false;
            }
            System.out.println(" }");

            //Lambdas
            for(LambdaInfo li : ci.lambdas){
                System.out.println("    Lambda: " + li.lambdaVarName +" (" + li.paramName + ":" + li.paramType + ") -> " + li.retType);
            }

            // Methods
            System.out.println("  Methods:");
            for (String methodName : ci.methods.keySet()) {
                MethodInfo mi = ci.methods.get(methodName);
                System.out.println("    Method: " + methodName);
                System.out.println("      ReturnType: " + mi.returnType);

                // Parameters
                System.out.print("      Parameters: { ");
                boolean firstParam = true;
                for (String p : mi.paramOrder) {
                    if (!firstParam) System.out.print(", ");
                    System.out.print(p + "=" + mi.parameters.get(p));
                    firstParam = false;
                }
                System.out.println(" }");

                // Lambdas
                for(LambdaInfo li : mi.lambdas){
                    System.out.println("      Lambda: " + li.lambdaVarName +" (" + li.paramName + ":" + li.paramType + ") -> " + li.retType);
                }

                // Local variables
                System.out.print("      LocalVars: { ");
                boolean firstLocal = true;
                for (Map.Entry<String, String> lv : mi.localVars.entrySet()) {
                    if (!firstLocal) System.out.print(", ");
                    System.out.print(lv.getKey() + "=" + lv.getValue());
                    firstLocal = false;
                }
                System.out.println(" }");
            }
        }
    }

    public void checkOverriding(){
        for(String className : classes.keySet()){
            ClassInfo cinfo = classes.get(className);
            if(cinfo.parentName != null){
                ClassInfo parent = classes.get(cinfo.parentName);
                if(parent==null){continue;}
                for(String methodName : cinfo.methods.keySet()){
                    MethodInfo minfo = cinfo.methods.get(methodName);
                    MethodInfo parentMethod= getMethod(cinfo.parentName,methodName);
                    if(parentMethod != null){
                        if(!possibleTypes(parentMethod.returnType,minfo.returnType)){
                            System.out.println("Type error");
                            if(debug){
                                System.err.println("Overriding failed. Wrong return type");
                            }
                            System.exit(0);
                        }
                        if(minfo.parameters.size()!=parentMethod.parameters.size()){
                            System.out.println("Type error");
                            if(debug){
                                System.err.println("Overriding failed. Different number of parameters");
                            }
                            System.exit(0);
                        }
                        for(int i=0;i<minfo.parameters.size();i++){
                            String name = minfo.paramOrder.get(i);
                            String type = minfo.parameters.get(name);
                            String parentName = parentMethod.paramOrder.get(i);
                            String parentType = parentMethod.parameters.get(parentName);
                            if(!type.equals(parentType)){
                                System.out.println("Type error");
                                if(true){
                                    System.err.println("Overriding failed. Parameter type mismatch");
                                }
                                System.exit(0);
                            }
                        }
                    }
                }
            }
        }
    }
    
}

class ClassInfo{
    public String className;
    public String parentName;
    public Map<String,String> fields = new HashMap<>();
    public Map<String,MethodInfo> methods = new HashMap<>();
    public List<LambdaInfo> lambdas = new ArrayList<>(); // Lambdas in this class
    public ClassInfo(String className,String parentName){
        this.className = className;
        this.parentName = parentName;
    }
    public void addField(String varName, String varType){
        fields.put(varName,varType);
    }
    public void addMethod(String methodName, String returnType){
        methods.put(methodName,new MethodInfo(methodName,returnType));
    }
}

class MethodInfo{
    public String methodName;
    public String returnType;
    public Map<String,String> parameters = new HashMap<>();
    public List<String> paramOrder = new ArrayList<>();
    public Map<String,String> localVars = new HashMap<>();
    public List<LambdaInfo> lambdas = new ArrayList<>();
    public MethodInfo(String methodName, String returnType){
        this.methodName = methodName;
        this.returnType = returnType;
    }
    public void addParam(String param, String paramType){
        parameters.put(param,paramType);
        paramOrder.add(param);
    }
    public void addLocalVar(String varName, String varType){
        localVars.put(varName,varType);
    }
    public void removeLocalVar(String varName){
        localVars.remove(varName);
    }

    public void addLambda(LambdaInfo linfo){
        lambdas.add(linfo);
    }
}

class LambdaInfo{
    String retType;
    String paramName;
    String paramType;
    String body;
    String lambdaVarName;
    public LambdaInfo(String varName,String paramName, String paramType, String retType, String body){
        this.paramName = paramName;
        this.paramType = paramType;
        this.retType = retType;
        this.body = body;
        this.lambdaVarName = varName;
    }
}