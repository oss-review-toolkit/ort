/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

/*
 * All definitions are based on the specification for SPDX expressions:
 * https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
grammar SpdxExpression;

@header {
package org.ossreviewtoolkit.utils.spdx;
}

/*
 * Parser Rules
 */

licenseReferenceExpression
    :
    DOCUMENTREFERENCE | LICENSEREFERENCE
    ;

licenseIdExpression
    :
    IDSTRING
    PLUS?
    ;

simpleExpression
    :
    licenseReferenceExpression
    | licenseIdExpression
    ;

compoundExpression
    :
    simpleExpression
    | simpleExpression WITH (IDSTRING | LICENSEREFERENCE)
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
PLUS  : '+' ;

DOCUMENTREFERENCE : 'DocumentRef-' IDSTRING ':' LICENSEREFERENCE ;
LICENSEREFERENCE  : 'LicenseRef-' IDSTRING ;
IDSTRING          : (ALPHA | DIGIT)(ALPHA | DIGIT | '-' | '.')* ;

WHITESPACE : ' ' -> skip ;
