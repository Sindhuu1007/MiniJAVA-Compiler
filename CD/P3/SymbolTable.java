import visitor.GJDepthFirst;
import java.util.*;

public class SymbolTable{
    public Map<String,ClassInfo> classes = new HashMap<>();
    public String currentClass = null;
    public String currentMethod = null;

    public void addClass(String name,String parentName){    
        classes.put(name,new ClassInfo(name,parentName));
    }

    public void addField(String className,String var,String type){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            toAdd.addField(var,type);
        }
    }

    public void addMethod(String className,String method, String returnType){
        ClassInfo toAdd = classes.get(className);
        if(toAdd != null){
            MethodInfo minfo = new MethodInfo(method,returnType);
            minfo.methodLabel = className + "_" + method;
            toAdd.addMethod(method,minfo);
        }
    }

    public void addParameter(String classname,String method,String parameter,String type){
        MethodInfo minfo = getMethod(classname,method);
        if(minfo != null){
            minfo.addParam(parameter,type);
        }
    }

    public void addLocalVar(String className,String method,String var,String type){
            MethodInfo minfo = getMethod(className,method);
        if(minfo != null){
            minfo.addLocalVar(var,type);
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
                    /*for(LambdaInfo linfo : minfo.lambdas){
                        if (linfo.lambdaVarName.equals(varName)) {
                            return linfo.getLambdaString();
                        }
                        if(linfo.paramName.equals(varName)){
                            return linfo.paramType;
                        }
                    }
                        */
                }
            }
            if(toCheck.fields.containsKey(varName)){
                return toCheck.fields.get(varName);
            }
            /*
            for(LambdaInfo linfo : toCheck.lambdas){
                if(linfo.lambdaVarName.equals(varName)) {
                    return linfo.getLambdaString();
                }
                if(linfo.paramName.equals(varName)) {
                    return linfo.paramType;
                }
            }
                */
            if(toCheck.parentName != null){
                return getVariableType(toCheck.parentName, null, varName); // Search in parent, but not in a specific method
            }
        }
        return null;
    }

    public String getMethodReturnType(String className,String methodName){
        MethodInfo minfo = getMethod(className,methodName);
        if(minfo!=null){
            return minfo.returnType;
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
            if(toCheck.parentName!=null){
                //System.out.println("current="+toCheck.parentName);
                return getMethod(toCheck.parentName,methodName);
            }
        }
        return null;
    }

    public void computeOffsetsandTable(){
        for(ClassInfo cinfo : classes.values()){
            cinfo.computeVTable(this);
        }
        for(ClassInfo cinfo : classes.values()){
            cinfo.computeOffset(this);
        }
        assignTemporaries();
    }

    // --- map TEMP strings (e.g. "TEMP 3") to concrete allocated class name ---
    private Map<String,String> tempClass = new HashMap<>();

    public void recordTempClass(String temp, String className){
        tempClass.put(temp, className);
    }

    public String getClassForTemp(String temp){
        return tempClass.get(temp);
    }

    public void assignTemporaries(){
        //t0->this ki, t1,t2... emo arguments ki
        //method ki maatrame avi local. Method ki method ki madhya same temps vaadachu mana grammar prakaram.
        for(ClassInfo cinfo : classes.values()){
            for(MethodInfo minfo : cinfo.methods.values()){
                minfo.paramTemp.clear();
                minfo.localTemp.clear();
                int index = 1;
                for(String par : minfo.paramOrder){
                    minfo.paramTemp.put(par,index);
                    index++;
                }
                for(String loc : minfo.localVars.keySet()){
                    minfo.localTemp.put(loc,index);
                    index++;
                }
            }
        }
    }

    public String getTempForVariable(String className, String methodName, String varName){
        ClassInfo cinfo = classes.get(className);
        if(cinfo!=null){
            MethodInfo minfo = cinfo.methods.get(methodName);
            if(minfo!=null){
                if(minfo.localTemp.containsKey(varName)){
                    return "TEMP " + minfo.localTemp.get(varName);
                }
                if(minfo.paramTemp.containsKey(varName)){
                    return "TEMP " + minfo.paramTemp.get(varName);
                }
            }
            //method lopala ledhu => field avvali. Can be self field of parents' chain field
            if(getFieldOffset(className, varName) != -1){
                return null;
            }
        }
        return null;
    }

    public int getFieldOffset(String className,String varName){
        ClassInfo cinfo = classes.get(className);
        if(cinfo!=null){
            Integer offset = cinfo.fieldOffsets.get(varName);
            if(offset!=null){
                return offset.intValue();
            }
            else if(cinfo.parentName!=null){
                return getFieldOffset(cinfo.parentName,varName);
            }
        }
        return -1;
    }

    public int getMethodOffset(String className, String methodName){
        ClassInfo cinfo = classes.get(className);
        if (cinfo != null) {
            Integer offset = cinfo.methodOffsets.get(methodName);
            if (offset != null) {
                return offset.intValue();
            }
            if (cinfo.parentName != null) {
                return getMethodOffset(cinfo.parentName, methodName);
            }
        }
         throw new RuntimeException("Method " + methodName + " not found in class " + className);
    }
    
    public int getClassSize(String className){
        ClassInfo cinfo = classes.get(className);
        if(cinfo==null){return 4;}
        return 4 + (cinfo.fieldCount*4);
    }

    public void printOffsets() {
        for (String cname : classes.keySet()) {
            ClassInfo c = classes.get(cname);
            System.out.println("Class " + cname + " (extends " + c.parentName + ")");
            System.out.println("  VTable:");
            for (int i = 0; i < c.vTable.size(); i++) {
                System.out.println("    [" + i + "] " + c.vTable.get(i).methodLabel);
            }
            System.out.println("  Field Offsets:");
            for (String f : c.fieldOffsets.keySet()) {
                System.out.println("    " + f + " -> " + c.fieldOffsets.get(f));
            }
            System.out.println();
        }
    }
    
}

class ClassInfo{
    public String className;
    public String parentName;
    public Map<String,String> fields = new LinkedHashMap<>();
    public Map<String,Integer> fieldOffsets = new LinkedHashMap<>();
    public Map<String,MethodInfo> methods = new LinkedHashMap<>();
    public Map<String,Integer> methodOffsets = new LinkedHashMap<>();
    public List<MethodInfo> vTable = new ArrayList<>();
    public int fieldCount = 0;
    public int vTableSize = 0;
    private boolean vTableComputed = false;
    private boolean offsetComputed = false;

    public List<LambdaInfo> lambdas = new ArrayList<>();

    public ClassInfo(String className,String parentName){
        this.className = className;
        this.parentName = parentName;
    }

    public void addField(String varName, String varType){
        fields.put(varName,varType);
    }
    public void addMethod(String methodName, MethodInfo minfo){
        methods.put(methodName,minfo);
    }
    public void computeOffset(SymbolTable st){
        if(offsetComputed) return;
        offsetComputed = true;
        int fieldOffset = 4;
        if(parentName != null){
            ClassInfo parentInfo = st.classes.get(parentName);
            if(parentInfo != null){
                parentInfo.computeOffset(st);
                fieldOffset = parentInfo.fieldCount*4 + 4;
                fieldOffsets.putAll(parentInfo.fieldOffsets);
                methodOffsets.putAll(parentInfo.methodOffsets);
            }
        }
        for(String f : fields.keySet()){
            if(!fieldOffsets.containsKey(f)){
                fieldOffsets.put(f,fieldOffset);
                fieldOffset += 4;
            }
        }
        fieldCount = (fieldOffset - 4) / 4;
        vTableSize = vTable.size() * 4;
    }
    public void computeVTable(SymbolTable st){
        if(vTableComputed) return;
        if(parentName != null){
            ClassInfo parentInfo = st.classes.get(parentName);
            if(parentInfo != null){
                parentInfo.computeVTable(st);
                vTable.addAll(parentInfo.vTable);
            }
        }
        for(MethodInfo m : methods.values()){
            boolean found = false;
            for(int i=0;i<vTable.size();i++){
                if(vTable.get(i).methodName.equals(m.methodName)){
                    vTable.set(i,m);
                    found = true;
                    break;
                }
            }
            if(!found){
                vTable.add(m);
            }
        }
        for(int i=0;i<vTable.size();i++){
            methodOffsets.put(vTable.get(i).methodName,i*4);
        }
        vTableSize = vTable.size() * 4;
        vTableComputed = true;
    }
}

class MethodInfo{
    public String methodName;
    public String returnType;
    public Map<String,String> parameters = new LinkedHashMap<>();
    public List<String> paramOrder = new ArrayList<>();
    public Map<String,String> localVars = new LinkedHashMap<>();
    public String methodLabel;

    public List<LambdaInfo> lambdas = new ArrayList<>();
    public Map<String,Integer> paramTemp = new LinkedHashMap<>();
    public Map<String,Integer> localTemp = new LinkedHashMap<>();

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