%{
    #include <bits/stdc++.h>
    #include "P1.tab.h"
    void yyerror(const char *);
    int line_counter = 1;
    using namespace std;
%}

%%

"#define"       {return DEFINE;}

"import"        {return IMPORT;}
"java.util.function.Function"    {return FUNCTION;}
"class"         {return CLASS;}
"public"        {return PUBLIC;}
"static"        {return STATIC;}
"void"          {return VOID;}
"main"          {return MAIN;}
"int"           {return INT;}
"String"        {return STRING;}
"new"           {return NEW;}
"this"          {return THIS;}
"true"          {return T;}
"false"         {return F;}
"if"            {return IF;}
"else"          {return ELSE;}
"while"         {return WHILE;}
"boolean"       {return BOOLEAN;}
"return"        {return RETURN;}
"System.out.println"    {return PRINT;}
"extends"       {return EXTENDS;}
"length"        {return LENGTH;}
"Function"      {return FUNCTION_TEMP;}

"+"             {return PLUS;}
"-"             {return MINUS;}
"*"             {return MUL;}
"/"             {return DIVIDE;}

"!"             {return NOT;}

"="             {return ASSIGN;}

"!="            {return NE;}
"<="            {return LE;}

"("             {return OPEN;}
")"             {return CLOSE;}
"{"             {return OCURLY;}
"}"             {return CCURLY;}
"["             {return OSQ;}
"]"             {return CSQ;}
"<"             {return OANG;}
">"             {return CANG;}
";"             {return SEMICOLON;}
","             {return COMMA;}
"."             {return FULLSTOP;}

"&&"            {return AND;}
"||"            {return OR;}
"->"            {return ARROW;}

0|[1-9][0-9]*   {yylval.numval = atoi(yytext); return INTEGER_LITERAL;}
[a-zA-Z_][a-zA-Z0-9_]* {yylval.strval = strdup(yytext);return IDENTIFIER;}

[ \t\r]+          { /* ignore whitespace */ }
\n              {line_counter++;}

"//".*          {/* ignore -> single line comments*/}

. {yyerror("Unknownchar\n");return 0;}

%%

int yywrap(void) {
    return 1;
}