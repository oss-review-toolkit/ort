/*
 * All definitions are based on the specification for SPDX expressions:
 * https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */

grammar SpdxExpression;

@header {
package com.here.ort.model.spdx;
}

/*
 * Parser Rules
 */

licenseRefExpression
    :
    LICENSEREF
    ;

licenseExceptionExpression
    :
    IDSTRING
    ;

licenseIdExpression
    :
    IDSTRING
    (PLUS)?
    ;

simpleExpression
    :
    licenseRefExpression
    | licenseIdExpression
    ;

compoundExpression
    :
    simpleExpression
    | simpleExpression WITH licenseExceptionExpression
    | compoundExpression AND compoundExpression
    | compoundExpression OR compoundExpression
    | OPEN compoundExpression CLOSE
    ;

licenseExpression
    :
    (simpleExpression | compoundExpression)
    EOF
    ;


/*
 * Lexer Rules
 */

fragment ALPHA : [A-Za-z] ;
fragment DIGIT : [0-9] ;

AND  : ('AND' | 'and') ;
OR   : ('OR' | 'or') ;
WITH : ('WITH' | 'with') ;

OPEN  : '(' ;
CLOSE : ')' ;
PLUS  : '+';

LICENSEREF : ('DocumentRef-' | 'LicenseRef-') IDSTRING ;
IDSTRING   : (ALPHA | DIGIT)(ALPHA | DIGIT | '-' | '.')* ;

WHITESPACE : ' ' -> skip ;
