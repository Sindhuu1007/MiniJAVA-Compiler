%{
    #include <bits/stdc++.h>
    #include <stdio.h>
    #include <stdlib.h>
    #include <string.h>
    using namespace std;
    void yyerror(const char *);
    int yylex(void);
    extern int line_counter;
    extern char* yytext;
    
    int indent = 0;
    string indentfn(const string &s){
        string ans = "";
        for(int i=0;i<indent;i++){
            ans += "\t";
        }
        ans += s;
        return ans;
    }

    struct macroStruct{
        vector<string> parameters;
        string macrobody;
        bool isFromExpr;
    };
    //Macros handling*
    map<string,macroStruct> macrosMap;

    bool isIden(const string& s){
        if(!(isalpha(s[0]) || s[0]=='_')){return false;}
        for(int i=1;i<s.size();i++){
            char c = s[i];
            if(!(isalnum(c) || c=='_')){return false;}
        }
        return true;
    }

    vector<string> breakArguments(const string &s){
        vector<string> result;
        if(s.empty()){return result;}
        string argument = "";
        int bctCnt = 0;
        for(char c : s){
            if(c==',' && bctCnt==0){
                int l = 0;
                while(l<argument.size() && (argument[l] == ' ' || argument[l] == '\t')){l++;}
                int r = argument.size()-1;;
                while(r>=l && (argument[r] == ' ' || argument[r] == '\t')){r--;}
                if(l<=r){
                    result.push_back(argument.substr(l,r-l+1));
                }
                argument = "";
            }
            else{
                if(c=='('){bctCnt++;}
                if(c==')'){bctCnt--;}
                argument += c;
            }
        }
        int l = 0;
        while(l<argument.size() && (argument[l] == ' ' || argument[l] == '\t')){l++;}
                int r = argument.size()-1;;
        while(r>=l && (argument[r] == ' ' || argument[r] == '\t')){r--;}
        if(l<=r){
            result.push_back(argument.substr(l,r-l+1));
        }
        return result;
    }

    vector<string> tokaniseAction(const string &s){
        vector<string> result;
        string cur = "";
        for(char c : s){
            if(isalnum(c) || c=='_'){
                cur += c;
            }
            else{
                if(!cur.empty()){
                    result.push_back(cur);
                    cur = "";
                }
                result.push_back(string(1,c));
            }
        }
        if(!cur.empty()){result.push_back(cur);}
        return result;
    }

    string substituteMacro(const string& macroname,const vector<string>& args){
        const macroStruct& ms = macrosMap[macroname];
        if(args.size()!=ms.parameters.size()){
            yyerror("Invalid! No of arguments mismatch\n");
            return "";
        }
        unordered_map<string,string> mapping;
        for(int i=0;i<args.size();i++){
            mapping[ms.parameters[i]] = args[i];
        }
        vector<string> tokens = tokaniseAction(ms.macrobody);
        string result = "";
        for(string &s : tokens){
            if(mapping.count(s)){
                {result += "(" + mapping[s] + ")";}
            }
            else {result += s;}
        }
        return result;
    }
%}

%union {
    char* strval;
    int numval;
}

%token <strval> IDENTIFIER
%token <numval> INTEGER_LITERAL
%token DEFINE IMPORT FUNCTION CLASS PUBLIC STATIC VOID MAIN INT STRING NEW THIS T F IF ELSE WHILE BOOLEAN RETURN PRINT EXTENDS LENGTH FUNCTION_TEMP PLUS MINUS MUL DIVIDE NOT ASSIGN NE LE OPEN CLOSE OCURLY CCURLY OSQ CSQ OANG CANG SEMICOLON COMMA FULLSTOP AND OR ARROW
%type <strval> Goal ImportFunctionOpt MainClass MultiTypeDeclaration TypeDeclaration MultiMethodDeclaration MethodDeclaration VarDecl Type MultiStatements Statement OptExpressions OptExpressionsHelper Expression PrimaryExpression MultiMacroDefinition MacroDefinition MacroDefExpression MacroDefStatement ParameterOpt ParameterOptHelper Matched Unmatched NonIFStatement LambdaParameter Extra

%left OR
%left AND
%nonassoc NE
%nonassoc LE
%left PLUS MINUS
%left MUL DIVIDE
%right NOT
%right ARROW

%%

Goal : 
    ImportFunctionOpt MultiMacroDefinition MainClass MultiTypeDeclaration {
        $$ = strdup((string($1) + string($3) + string($4)).c_str());
        cout<<$$<<"\n";
    }
    

ImportFunctionOpt:
    IMPORT FUNCTION SEMICOLON {$$ = strdup("import java.util.function.Function;\n\n");}
    | {$$ = strdup("");}
    

MainClass:
    CLASS IDENTIFIER OCURLY PUBLIC STATIC VOID MAIN OPEN STRING OSQ CSQ IDENTIFIER CLOSE OCURLY PRINT OPEN Expression CLOSE SEMICOLON CCURLY CCURLY {
        $$ = strdup(("class "+string($2)+" {\n" + "\tpublic static void main(String[] "+string($12)+" ){" + "\n\t\t"  + "System.out.println" +"(" + string($17) + ");\t}\n}\n\n").c_str());
    }
    

MultiTypeDeclaration : 
    MultiTypeDeclaration TypeDeclaration {$$ = strdup((string($1) + string($2) + "\n").c_str());}
    | {$$ = strdup("");}
    

TypeDeclaration: 
    CLASS IDENTIFIER OCURLY VarDecl MultiMethodDeclaration CCURLY {$$ = strdup(("class " + string($2) + " {\n" + string($4) + string($5)+ "}\n").c_str());}
    | CLASS IDENTIFIER EXTENDS IDENTIFIER OCURLY VarDecl MultiMethodDeclaration CCURLY {$$ = strdup(("class "+string($2) + " extends "+ string($4) + " {\n" + string($6) + string($7) + "}\n\n").c_str());}

MultiMethodDeclaration:
    MultiMethodDeclaration MethodDeclaration {$$ = strdup((string($1) + string($2)).c_str());}
    | {$$ = strdup("");}
    

MethodDeclaration:
    PUBLIC Type IDENTIFIER OPEN ParameterOpt CLOSE OCURLY VarDecl MultiStatements RETURN Expression SEMICOLON CCURLY{
        $$ = strdup(("public " + string($2) + " " + string($3) + " (" + string($5) + ") {\n" + string($8)  + string($9) + "return " + string($11) + ";\n}\n").c_str());
    }

VarDecl:
    VarDecl Type IDENTIFIER SEMICOLON{
        $$ = strdup((string($1) + string($2) + " " + string($3) + " ;\n").c_str());
    }
    | /*empty*/ {$$ = strdup("");}
    

Type:
    INT OSQ CSQ {$$ = strdup("int[]");}
    | BOOLEAN {$$ = strdup("boolean");}
    | INT {$$ = strdup("int");}
    | IDENTIFIER {$$ = strdup((string($1) + " ").c_str());}
    | FUNCTION_TEMP OANG IDENTIFIER COMMA IDENTIFIER CANG {$$ = strdup(("Function<" + string($3) + " ," + string($5) + " >").c_str());}
    

MultiStatements:
    Statement MultiStatements {$$ = strdup((string($1) + string($2)).c_str());}
    | /*empty*/ {$$ = strdup("");}

Statement:
    Matched
    | Unmatched

NonIFStatement:
    OCURLY MultiStatements CCURLY{
        $$ = strdup(("{\n" + string($2) + "}\n").c_str());
    }
    | PRINT OPEN Expression CLOSE SEMICOLON{
        $$ = strdup(("System.out.println(" + string($3) + ");\n").c_str());
    }
    | IDENTIFIER ASSIGN Expression SEMICOLON{
        $$ = strdup((string($1) + " = " + string($3) + ";\n").c_str());
    }
    | IDENTIFIER OSQ Expression CSQ ASSIGN Expression SEMICOLON{
        $$ = strdup((string($1) + " [" + string($3) + "] = " + string($6) + ";\n").c_str());
    }
    | WHILE OPEN Expression CLOSE Statement {
        $$ = strdup(("while(" + string($3) + ")\n" + string($5)).c_str());
    }
    | IDENTIFIER OPEN OptExpressions CLOSE SEMICOLON{
        string macroname = string($1);
        vector<string> args = breakArguments(string($3));
        if(macrosMap.count(macroname) && !macrosMap[macroname].isFromExpr){
            string expanded = substituteMacro(macroname,args);
            $$ = strdup((expanded).c_str());
        }
        else if(macrosMap.count(macroname) && macrosMap[macroname].isFromExpr){
            yyerror("Expected a macro call from expr, received from Statement\n");
            return 0;
        }
        else{
            //fn call annattu kadhaa....
            $$ = strdup((string($1) + "(" + string($3) + ");\n").c_str());
        }
    }

Matched:
    NonIFStatement {$$ = $1;}
    | IF OPEN Expression CLOSE Matched ELSE Matched {
        $$ = strdup(("if(" + string($3) + ")" + string($5) + "else " + string($7)).c_str());
    }
    
//This is just for handling the then part with proper indendation in case off if , else, while

Unmatched:
    IF OPEN Expression CLOSE Statement {
        $$ = strdup(("if(" + string($3) + ")\n" + string($5)).c_str());
    }
    | IF OPEN Expression CLOSE Matched ELSE Unmatched {
        $$ = strdup(("if(" + string($3) + ")\n\t" + string($5) + "else\n\t" + string($7)).c_str());
    }
    
OptExpressions:
    OptExpressionsHelper {$$ = $1;}
    | {$$ = strdup("");}
    
OptExpressionsHelper:
    Expression {$$ = $1;}
    | OptExpressionsHelper COMMA Expression {$$ = strdup((string($1) + "," + string($3)).c_str());}
    
Expression:
    PrimaryExpression {$$ = $1;}
    | PrimaryExpression AND PrimaryExpression {$$ = strdup((string($1) + "&&" + string($3)).c_str());}
    | PrimaryExpression OR PrimaryExpression {$$ = strdup((string($1) + "||" + string($3)).c_str());}
    | PrimaryExpression NE PrimaryExpression {$$ = strdup((string($1) + "!=" + string($3)).c_str());}
    | PrimaryExpression LE PrimaryExpression {$$ = strdup((string($1) + "<=" + string($3)).c_str());}
    | PrimaryExpression PLUS PrimaryExpression {$$ = strdup((string($1) + "+" + string($3)).c_str());}
    | PrimaryExpression MINUS PrimaryExpression {$$ = strdup((string($1) + "-" + string($3)).c_str());}
    | PrimaryExpression MUL PrimaryExpression {$$ = strdup((string($1) + "*" + string($3)).c_str());}
    | PrimaryExpression DIVIDE PrimaryExpression {$$ = strdup((string($1) + "/" + string($3)).c_str());}
    | PrimaryExpression OSQ PrimaryExpression CSQ {
        $$ = strdup((string($1) + "[" + string($3) + "]").c_str());
    }
    | PrimaryExpression FULLSTOP LENGTH {
        $$ = strdup((string($1) + ".length").c_str());}
    | PrimaryExpression FULLSTOP IDENTIFIER OPEN OptExpressions CLOSE {$$ = strdup((string($1) + "." + string($3) + " (" + string($5) + ")").c_str());}
    | IDENTIFIER OPEN OptExpressions CLOSE {
        string macroname = string($1);
        vector<string> args = breakArguments(string($3));
        if(macrosMap.count(macroname)  && macrosMap[macroname].isFromExpr){
            string expanded = substituteMacro(macroname,args);
            $$ = strdup((expanded).c_str());
        }
        else if(macrosMap.count(macroname) && !macrosMap[macroname].isFromExpr){
            yyerror("Expected a macro call from statement, received from expr\n");
            return 0;
        }
        else{
            //fn call annattu kadhaa....
            $$ = strdup((string($1) + " (" + string($3) + ")").c_str());
        }
    }
    | LambdaParameter Extra {$$ = strdup((string($1) + string($2)).c_str());}
    

LambdaParameter:
    OPEN IDENTIFIER CLOSE{
        $$ = strdup(("(" + string($2) + " )").c_str());
    }

Extra:
     ARROW Expression {$$ = strdup(("->" + string($2)).c_str());}
    | {$$ = strdup("");}

PrimaryExpression:
    INTEGER_LITERAL {$$ = strdup((to_string($1)).c_str());}
    | T {$$ = strdup("true");}
    | F {$$ = strdup("false");}
    | IDENTIFIER {$$ = strdup((string($1)).c_str());}
    | THIS {$$ = strdup("this");}
    | NEW INT OSQ Expression CSQ {$$ = strdup(("new int[" + string($4) + "]").c_str());}
    | NEW IDENTIFIER OPEN CLOSE {$$ = strdup(("new " + string($2) + " ()").c_str());}
    | NOT Expression {$$ = strdup(("!" + string($2)).c_str());}
    | OPEN Expression CLOSE {$$ = strdup(("(" + string($2) + ")").c_str());}

MultiMacroDefinition : 
    MultiMacroDefinition MacroDefinition {$$ = strdup((string($1) + string($2)).c_str());}
    | {$$ = strdup("");}

MacroDefinition:
    MacroDefExpression {$$ = $1;}
    | MacroDefStatement {$$ = $1;}

MacroDefStatement:
    DEFINE IDENTIFIER OPEN OptExpressions CLOSE OCURLY MultiStatements CCURLY{
        macroStruct ms;
        ms.parameters = breakArguments(string($4));
        ms.macrobody = string($7);
        ms.isFromExpr = false;
        macrosMap[string($2)] = ms;
        $$ = strdup("");
    }

MacroDefExpression:
    DEFINE IDENTIFIER OPEN OptExpressions CLOSE Expression{
        macroStruct ms;
        ms.parameters = breakArguments(string($4));
        ms.macrobody = string($6);
        ms.isFromExpr = true;
        macrosMap[string($2)] = ms;
        $$ = strdup("");
    }

ParameterOpt:
    ParameterOptHelper {$$ = $1;}
    | {$$ = strdup("");}

ParameterOptHelper:
    Type IDENTIFIER {$$ = strdup((string($1) +" " +  string($2) + " ").c_str());}
    | ParameterOptHelper COMMA Type IDENTIFIER {$$ = strdup((string($1) + "," + string($3) + " " + string($4) + " ").c_str());}

%%

void yyerror(const char *s) {
    printf("// Failed to parse macrojava code.\n" );
    exit(1);
}

int main(void) {
    return yyparse();
}
